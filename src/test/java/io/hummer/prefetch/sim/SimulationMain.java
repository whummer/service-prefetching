package io.hummer.prefetch.sim;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.PrefetchingService.PrefetchRequest;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.client.PrefetchingCapableClient;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.prefetch.context.Time;
import io.hummer.prefetch.context.TimeClock;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.impl.UsagePattern.UsagePatternPredictionBased;
import io.hummer.prefetch.sim.SimulationTestData.ServiceUsage;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntities;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntity;
import io.hummer.prefetch.sim.util.AuditEvent;
import io.hummer.prefetch.strategy.PrefetchStrategyContextBased;
import io.hummer.util.coll.Pair;
import io.hummer.util.log.LogUtil;
import io.hummer.util.math.MathUtil;
import io.hummer.util.test.GenericTestResult;
import io.hummer.util.test.GenericTestResult.IterationResult;
import io.hummer.util.test.GenericTestResult.ResultType;
import io.hummer.util.test.result.IterationBasedAggregatedDescriptiveStatistics.IterationBasedAggregatedDescriptiveStatisticsDefault;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import com.google.common.util.concurrent.AtomicDouble;

public class SimulationMain {

	static final Logger LOG = LogUtil.getLogger();

	public static void runWithConfig(MovingEntities ents, 
			PrefetchStrategy strat, int numSecsLookIntoContextFuture) throws Exception {
		int startSecs = 10000;
		double stopSecs = maxTimePoint.get();
		int stepSecs = 10;
		int lookIntoFutureSecs = 60*5;
		TimeClock.setTime(0);
		String stratID = strat.getClass().getSimpleName().replace("PrefetchStrategy", "").substring(0, 1);

		/* get service usages */
		List<ServiceUsage> usages = new LinkedList<ServiceUsage>();
		usages.add(SimulationTestData.getServiceUsage1());
		usages.add(SimulationTestData.getServiceUsage2());
		usages.add(SimulationTestData.getServiceUsage3());
		UsagePattern usageCombined = ServiceUsage.combine(
				usages.toArray(new ServiceUsage[0]));

		/* initialize clients */
		Map<MovingEntity,PrefetchingCapableClient> clients = 
				new HashMap<>();
		for(MovingEntity ent : ents.entities) {
			Context<Object> ctx = new Context<>();
			PrefetchingCapableClient client = 
					new PrefetchingCapableClient(ctx);
			clients.put(ent, client);

			/* add subscriptions for service prefetchings */
			for(ServiceUsage usage : usages) {
				/* set context TODO hacky*/
				if(((UsagePatternPredictionBased)usage.pattern).context == null) {
					((UsagePatternPredictionBased)usage.pattern).context = ctx;
				}
				/* prepare prefetch request*/
				PrefetchRequest r = new PrefetchRequest();
				r.invocationPredictor = usage.invocationPredictor;
				r.strategy = strat;
				r.lookIntoFutureSecs = lookIntoFutureSecs;
				if(strat instanceof PrefetchStrategyContextBased) {
					r.strategy = new PrefetchStrategyContextBased(
							usage.pattern, (int)(numSecsLookIntoContextFuture / stepSecs), stepSecs);
				}
				client.setPrefetchingStrategy(r);
			}
		}

		/* start main loop */
		for(int t = startSecs; t < stopSecs; t += stepSecs) {
			//System.out.println(t);
			int secs = t - startSecs;
			System.out.println("Time: " + t);
			TimeClock.setTime(t);

			for(int j : snapshotTimeSteps) {
				if(secs % j == 0) {
					String prefix = "s" + stratID + "m" + j + "f" + numSecsLookIntoContextFuture + 
							"t" + secs /* + "i" + (secs / j) +*/;
					test.addEntry(prefix + "hit", prefetchHits.getLastStatistics(j).getSecond().getSum());
					test.addEntry(prefix + "miss", prefetchMisses.getLastStatistics(j).getSecond().getSum());
					test.addEntry(prefix + "unused", resultsUnused.getLastStatistics(j).getSecond().getSum());
					test.addEntry(prefix + "hitTotal", prefetchHits.getStatistics().getSum());
					test.addEntry(prefix + "missTotal", prefetchMisses.getStatistics().getSum());
					test.addEntry(prefix + "unusedTotal", resultsUnused.getStatistics().getSum());
					test.addEntry(prefix + "resultAge",
							new MathUtil().average(TimeClock.
									filterTimestampedValues(resultAges, t-j, t)));

					if(firstStrategyIteration.get()) {
						test.addEntry(prefix + "failed", failedInvocations.getStatistics().getSum());
						test.addEntry(prefix + "success", successInvocations.getStatistics().getSum());
					}
				}
			}

			if(firstStrategyIteration.get()) {
				if(secs < 3000) {
					test.addEntryAndRemoveOldIfSame(
							"t[0-9]+usage", "t" + secs + "usage",
					usageCombined.predictUsage(t), result);
				}
			}

			/* move forward */
			for(MovingEntity ent : ents.entities) {
				PathPoint loc = ent.getLocationAtTime(t);
				if(loc != null) {
					dataPointsGross.addAndGet(1);
					PrefetchingCapableClient client = 
							clients.get(ent);
					Context<Object> ctx = client.getContext();
					Map<String,Object> ctxUpdates = new HashMap<>();
					/* set current time */
					ctxUpdates.put(Context.ATTR_TIME, new Time((double)t));
					/* set predicted path */
					ctxUpdates.put(Context.ATTR_PATH, ent.path);
					ctxUpdates.put(Context.ATTR_FUTURE_PATH, ent.getFuturePathAt(t));
					/* set current location */
					ctxUpdates.put(Context.ATTR_LOCATION, loc);
					ctxUpdates.put(Context.ATTR_LOCATION_LAT, loc.coordinates.lat);
					ctxUpdates.put(Context.ATTR_LOCATION_LON, loc.coordinates.lon);
					/* set network availability */
					boolean netAvail = loc.cellNetworkCoverage.hasAnyCoverage();
					ctxUpdates.put(Context.ATTR_NETWORK_AVAILABLE, netAvail);
					if(firstStrategyIteration.get()) {
						test.addEntryAndRemoveOldIfSame(
							"e" + ent.id + "t[0-9]+linkSpeed",
							"e" + ent.id + "t" + secs + "linkSpeed",
							loc.cellNetworkCoverage.getMaxSpeed().getCapacityKbitPerSec(), result);
					}
					/* do the context update */
					ctx.setContextAttributes(ctxUpdates);
					LOG.info("Car " + ent.id + ": coverage at " + t + "(" + loc.time + 
							"): " + loc.cellNetworkCoverage.hasAnyCoverage());
					/* make invocations */
					for(ServiceUsage u : usages) {
//						if(u.pattern.predictUsage(t) > 0) {
							try {
								List<Pair<Context<Object>,ServiceInvocation>> invs = 
										u.invocationPredictor.predictInvocations(
												ctx, new Time(t), new Time(t + stepSecs));
								//System.out.println("client inv: " + Util.toString(inv));
								for(Pair<Context<Object>,ServiceInvocation> inv : invs) {
									client.invoke(inv.getSecond());
								}
							} catch (IllegalStateException e) {
								/* swallow invocation errors (due to lack of 
								 * network connectivity, which we simulate) */
							}
//						}
					}
				}
			}
		}
	}

	static void createGraphs() throws Exception {
		GenericTestResult r1 = GenericTestResult.load(resultFile);

		for(PrefetchStrategy strat : strategies) {
			String stratID = strat.getClass().getSimpleName()
					.replace("PrefetchStrategy", "").substring(0, 1);
			for(int j : snapshotTimeSteps) {
				for(int f : numsSecsLookIntoContextFuture) {
					String prefix = "s" + stratID + "m" + j + "f" + f;
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)hit", 1), 
							new String[]{prefix + "t<level>hit", prefix + "t<level>miss"},
							new String[]{"Prefetch Hits", "Prefetch Misses"}, 
							ResultType.MEAN, "Time", "Value", "etc/result_" + prefix + "_hitmiss.pdf");
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)resultAge", 1), 
							new String[]{prefix + "t<level>resultAge"},
							new String[]{"Prefetched Result Age"}, 
							ResultType.MEAN, "Time", "Seconds", "etc/result_" + prefix + "_age.pdf");
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)unused", 1), 
							new String[]{prefix + "t<level>unused"},
							new String[]{"Unused Invocations"}, 
							ResultType.MEAN, "Time", "Value", "etc/result_" + prefix + "_unused.pdf");
				}
			}
			String prefix = "s" + stratID + "m" + snapshotTimeSteps.get(0);
			int futSecs1 = numsSecsLookIntoContextFuture.get(0); 
			int futSecs2 = numsSecsLookIntoContextFuture.get(1); 
			int futSecs3 = numsSecsLookIntoContextFuture.get(2); 
			r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "f" + numsSecsLookIntoContextFuture.get(0) + "t([0-9]+)miss", 1), 
					new String[]{ 	prefix + "f" + futSecs1 + "t<level>miss",
									prefix + "f" + futSecs2 + "t<level>miss",
									prefix + "f" + futSecs3 + "t<level>miss"	},
					new String[]{"t_f = " + futSecs1 + "sec","t_f = " + futSecs2 + "sec","t_f = " + futSecs3 + "sec"}, 
					ResultType.MEAN, "Time", "Prefetch Misses", "etc/result_" + prefix + "_misses.pdf");

			r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "f" + futSecs1 + "t([0-9]+)failed", 1), 
					new String[]{prefix + "f" + futSecs1 + "t<level>failed", prefix + "f" + futSecs1 + "t<level>success"}, 
					new String[]{"Failed Invocations", "Successful Invocations"}, 
					ResultType.MEAN, "Time", "Value", "etc/result_success.pdf");
		}
		String prefix = "";
		r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)usage", 1), 
				new String[]{prefix + "t<level>usage"}, 
				new String[]{"Service Data Usage"}, 
				ResultType.MEAN, "Time", "Data Rate (kbps)", "etc/result_usage.pdf");
		String clientID = "1";
		String prefix1 = "e" + clientID;
		String pattern = "(" + prefix1 + ")?t([0-9]+)((linkSpeed)|(usage))";
		//System.out.println(r1.getAllLevelIDsByPattern(pattern, 2).toString().replace(",", "\n"));
		r1.createGnuplot(r1.getAllLevelIDsByPattern(pattern, 2),
				new String[]{prefix + "t<level>usage", prefix1 + "t<level>linkSpeed"}, 
				new String[]{"Service Data Usage", "Estimated Available Link Speed"},
				ResultType.MEAN, "Simulation Time (sec)", "Data Rate (kbps)", "etc/result_client1_linkSpeed.pdf",
				"set logscale y", "set yrange [1:1000000]", "set xrange [2970:*]",
				//"set xtics format '%s%c'"
				//"set xtics rotate by 90 offset 0,-2"
				"set format x '%.1tK'",
				"set format y '10^{%T}'"
				);
	}

	static GenericTestResult result = new GenericTestResult();
	static IterationResult test = result.newIteration();
	static final IterationBasedAggregatedDescriptiveStatisticsDefault successInvocations = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	static final IterationBasedAggregatedDescriptiveStatisticsDefault failedInvocations = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	static final IterationBasedAggregatedDescriptiveStatisticsDefault prefetchHits = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	static final IterationBasedAggregatedDescriptiveStatisticsDefault prefetchMisses = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	static final IterationBasedAggregatedDescriptiveStatisticsDefault resultsUnused = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	//static final AtomicInteger prefetchMisses = new AtomicInteger();
	static final AtomicLong dataPoints = new AtomicLong();
	static final AtomicLong dataPointsGross = new AtomicLong();
	static final AtomicDouble maxTimePoint = new AtomicDouble();
	static final Map<Double,Double> resultAges = new HashMap<>();
	static final AtomicBoolean firstStrategyIteration = new AtomicBoolean(true);
	static final List<? extends PrefetchStrategy> strategies = Arrays.asList(
//			new PrefetchStrategyPeriodic(60),
			new PrefetchStrategyContextBased(null, 0, 0)
//			, new PrefetchStrategyNone()
			);
	static final List<Integer> numsSecsLookIntoContextFuture = 
		Arrays.asList(30, 300, 1800);
	static final List<Integer> snapshotTimeSteps = Arrays.asList(500, 1000);
	static final String resultFile = SimulationMain.class.getResource("/").getPath() + "/../../etc/sim_result.xml";

	public static void startSimulation(String serviceURL) throws Exception {

		SimulationTestData.setServiceURL(serviceURL);

		int numCars = 30;
		MovingEntities entities = SimulationTestData.getData(1, numCars);
//		MovingEntities entities = SimulationTestData.getData(25, 26);

		for(MovingEntity ent : entities.entities) {
			dataPoints.addAndGet(ent.path.size());
			double maxTime = ent.path.points.get(ent.path.points.size() - 1).time.time;
			if(maxTime > maxTimePoint.get()) {
				maxTimePoint.set(maxTime);
			}
		}
		///* add usage patterns to entities */
		//SimulationTestData.addUsagePatterns(entities);
		
		test.addEntry("dataPoints", dataPoints.get());

		System.out.println("Starting simulation...");
		System.out.println(maxTimePoint.get());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				test.addEntry("dataPointsGross", dataPointsGross.get());
				result.save(resultFile);
			}
		});
		AuditEvent.addListener(".*", new AuditEvent.EventListener() {
			public void notify(AuditEvent e) {
				if(e.type.equals(AuditEvent.E_INV_FAILED)) {
					failedInvocations.addValue(TimeClock.now(), 1);
				} else if(e.type.equals(AuditEvent.E_INV_SUCCESS)) {
					//System.out.println(e); // TODO
					successInvocations.addValue(TimeClock.now(), 1);
				} else if(e.type.equals(AuditEvent.E_PREFETCH_HIT)) {
					System.out.println(e); // TODO
					prefetchHits.addValue(TimeClock.now(), 1);
					@SuppressWarnings("unchecked")
					double resultTime = (double)((Map<String,Object>)e.data).get(Context.ATTR_TIME);
					//System.out.println("age: " + (TimeClock.now() - resultTime));
					resultAges.put(TimeClock.now(), TimeClock.now() - resultTime);
				} else if(e.type.equals(AuditEvent.E_PREFETCH_MISS)) {
					System.out.println(e); // TODO
					prefetchMisses.addValue(TimeClock.now(), 1);
				} else if(e.type.equals(AuditEvent.E_UNUSED_RESULT)) {
					resultsUnused.addValue(TimeClock.now(), 1);
				}
			}
		});

		for(PrefetchStrategy s : strategies) {
			for(int numSecsLookIntoContextFuture : numsSecsLookIntoContextFuture) {
				failedInvocations.clear();
				successInvocations.clear();
				prefetchHits.clear();
				prefetchMisses.clear();
				resultAges.clear();
				runWithConfig(entities, s, numSecsLookIntoContextFuture);
				firstStrategyIteration.set(false);
				System.gc();
			}
		}
	}


	public static void main(String[] args) throws Exception {
		boolean startServices = true;
		boolean startClients = true;
		String serviceHost = "localhost";
		if(args.length > 0 && args[0].equals("services")) {
			startClients = false;
		} else if(args.length > 0 && args[0].equals("clients")) {
			startServices = false;
			serviceHost = args[1];
		}
		String serviceURL = "http://" + serviceHost + ":8283/traffic";
		if(startServices) {
			/* deploy services */
			SimulationTestData.deployTestServices(serviceURL);
		}
		// draw graphs
		boolean createGraphs = true;
		if(createGraphs && new File(resultFile).exists()) {
			createGraphs();
			System.exit(0);
		}
		if(startClients) {
			startSimulation(serviceURL);
			System.exit(0);
		}
	}

}
