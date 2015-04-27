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
import io.hummer.prefetch.strategy.PrefetchStrategyPeriodic;
import io.hummer.util.coll.Pair;
import io.hummer.util.log.LogUtil;
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
	static final IterationBasedAggregatedDescriptiveStatisticsDefault resultAges = 
			new IterationBasedAggregatedDescriptiveStatisticsDefault();
	//static final AtomicInteger prefetchMisses = new AtomicInteger();
	static final AtomicLong dataPoints = new AtomicLong();
	static final AtomicLong dataPointsGross = new AtomicLong();
	static final AtomicDouble maxTimePoint = new AtomicDouble();
	static final AtomicBoolean firstStrategyIteration = new AtomicBoolean(true);
	static final List<? extends PrefetchStrategy> strategies = Arrays.asList(
			new PrefetchStrategyPeriodic(2, "P1"),
			new PrefetchStrategyPeriodic(10, "P2"),
			new PrefetchStrategyContextBased(null, 0, 0, "C")
//			, new PrefetchStrategyNone()
			);
	static final double startTime = System.currentTimeMillis();
	static final List<Integer> numsSecsLookIntoContextFuture = 
		Arrays.asList(30, 180, 
				900);
	static final List<Integer> snapshotTimeSteps = Arrays.asList(500, 1000);
	static final String resultFile = SimulationMain.class.getResource("/").getPath() + "/../../etc/sim_result.xml";

	public static void runWithConfig(MovingEntities ents, 
			PrefetchStrategy strat, int secsLookIntoContextFuture) throws Exception {
		int startSecs = 15000;
		double stopSecs = 31000; //maxTimePoint.get(); //
		int stepSecs = 10;
		TimeClock.setTime(0);
		String stratID = strat.id;

		/* get service usages */
		List<ServiceUsage> usages = new LinkedList<ServiceUsage>();
		usages.add(SimulationTestData.getServiceUsage1());
		usages.add(SimulationTestData.getServiceUsage2());
		usages.add(SimulationTestData.getServiceUsage3());
		usages.add(SimulationTestData.getServiceUsage4());
		usages.add(SimulationTestData.getServiceUsage5());
		usages.add(SimulationTestData.getServiceUsage6());
		UsagePattern usageCombined = ServiceUsage.combine(
				usages.toArray(new ServiceUsage[0]));

		/* initialize clients */
		Map<MovingEntity,PrefetchingCapableClient> clients = 
				new HashMap<>();
		for(MovingEntity ent : ents.entities) {
			Context<Object> ctx = new Context<>();
			PrefetchingCapableClient client = new PrefetchingCapableClient(ctx);
			if(strat instanceof PrefetchStrategyContextBased) {
		    	ctx.addChangeListener(client);
			}
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
				r.lookIntoFutureSecs = secsLookIntoContextFuture;
				int stepsLookFutureToTriggerStrategy = 2;
//				int stepsLookFutureToTriggerStrategy = (int)(secsLookIntoContextFuture / stepSecs);
				if(strat instanceof PrefetchStrategyContextBased) {
					r.strategy = new PrefetchStrategyContextBased(
							usage.pattern, stepsLookFutureToTriggerStrategy, stepSecs);
				} else if(strat instanceof PrefetchStrategyPeriodic) {
					r.strategy = new PrefetchStrategyPeriodic(
							Math.max(20, secsLookIntoContextFuture/
									((PrefetchStrategyPeriodic)strat).timeoutSecs));
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
					String prefix = "s" + stratID + "m" + j + "f" + secsLookIntoContextFuture + 
							"t" + secs /* + "i" + (secs / j) +*/;
					test.addEntry(prefix + "hit", prefetchHits.getLastStatistics(j, t).getSecond().getSum());
					test.addEntry(prefix + "miss", prefetchMisses.getLastStatistics(j, t).getSecond().getSum());
					test.addEntry(prefix + "unused", resultsUnused.getLastStatistics(j, t).getSecond().getSum());
					test.addEntry(prefix + "resultAge", resultAges.getLastStatistics(j, t).getSecond().getMean());
					test.addEntry(prefix + "resultAgeMin", resultAges.getLastStatistics(j, t).getSecond().getMin());
					test.addEntry(prefix + "resultAgeMax", resultAges.getLastStatistics(j, t).getSecond().getMax());
					test.addEntry(prefix + "resultAgeQ1", resultAges.getLastStatistics(j, t).getSecond().getPercentile(25));
					test.addEntry(prefix + "resultAgeMed", resultAges.getLastStatistics(j, t).getSecond().getPercentile(50));
					test.addEntry(prefix + "resultAgeQ3", resultAges.getLastStatistics(j, t).getSecond().getPercentile(75));
					test.addEntry(prefix + "hitTotal", prefetchHits.getStatistics().getSum());
					test.addEntry(prefix + "missTotal", prefetchMisses.getStatistics().getSum());
					test.addEntry(prefix + "unusedTotal", resultsUnused.getStatistics().getSum());
					test.addEntry(prefix + "resultAgeTotal", resultAges.getStatistics().getSum());
//					test.addEntry(prefix + "resultAge",
//							new MathUtil().average(TimeClock.
//									filterTimestampedValues(resultAges, t-j, t)));

					if(firstStrategyIteration.get()) {
						test.addEntry(prefix + "failed", failedInvocations.getStatistics().getSum());
						test.addEntry(prefix + "success", successInvocations.getStatistics().getSum());
					}
				}
			}

			double maxTimeToMeasureLinkUsage = 3000;
			if(firstStrategyIteration.get()) {
				if(secs < maxTimeToMeasureLinkUsage) {
					double usage = usageCombined.predictUsage(t);
					if(usage > 0.0) {
						test.addEntryAndRemoveOldIfSame(
							"t[0-9]+usage", "t" + secs + "usage", usage, result);
					}
				}
			}

			/* move forward */
			for(MovingEntity ent : ents.entities) {
				PathPoint loc = ent.getLocationAtTime(t);
				if(loc != null) {
					dataPointsGross.addAndGet(1);
					PrefetchingCapableClient client = clients.get(ent);
					Context<Object> ctx = client.getContext();
					Map<String,Object> ctxUpdates = Context.generateUpdates(t, ent.path, loc);
					if(firstStrategyIteration.get()) {
						if(secs < maxTimeToMeasureLinkUsage) {
							test.addEntryAndRemoveOldIfSame(
								"e" + ent.id + "t[0-9]+linkSpeed",
								"e" + ent.id + "t" + secs + "linkSpeed",
								loc.cellNetworkCoverage.getMaxSpeed().getCapacityKbitPerSec(), result);
						}
					}
					/* do the context update */
					ctx.setContextAttributes(ctxUpdates);
					LOG.info("Car " + ent.id + ": coverage at " + t + "(" + loc.time + 
							"): " + loc.cellNetworkCoverage.hasSufficientCoverage());
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
									// for now, only run one invocation per time interval, hence break here
									break;
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

		List<String> stratIDs = new LinkedList<>();
		int stratNum = 0;
		for(PrefetchStrategy strat : strategies) {
			stratNum ++;
			String stratID = strat.id;
			stratIDs.add(stratID);
			for(int j : snapshotTimeSteps) {
				for(int f : numsSecsLookIntoContextFuture) {
					String prefix = "s" + stratID + "m" + j + "f" + f;
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)hit", 1), 
							new String[]{prefix + "t<level>hit", prefix + "t<level>miss"},
							new String[]{"Prefetch Hits", "Prefetch Misses"}, 
							ResultType.MEAN, "Simulation Time (sec)", "Value", "etc/result_" + prefix + "_hitmiss.pdf");
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)resultAge", 1),
							new String[]{prefix + "t<level>resultAgeQ1:" +
									prefix + "t<level>resultAgeMin:" +
									prefix + "t<level>resultAgeMax:" +
									prefix + "t<level>resultAgeQ3:" +
									prefix + "t<level>resultAge"//, prefix + "t<level>resultAge"
							},
							new String[]{"Prefetched Result Age"}, 
							ResultType.MEAN, "Simulation Time (sec)", "Result Age (sec)", "etc/result_" + prefix + "_age.pdf",
							GenericTestResult.CMD_DRAW_LINE_THROUGH_CANDLESTICKS, "set boxwidth 200");
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)resultAgeTotal", 1), 
							new String[]{prefix + "t<level>resultAgeTotal"},
							new String[]{"Prefetched Result Age"}, 
							ResultType.MEAN, "Simulation Time (sec)", "Result Age (sec)", "etc/result_" + prefix + "_ageTotal.pdf");
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
					new String[]{"t_p = " + futSecs1 + "sec","t_p = " + futSecs2 + "sec","t_p = " + futSecs3 + "sec"}, 
					ResultType.MEAN, "Simulation Time (sec)", "Prefetch Misses", "etc/result_" + prefix + "_misses.pdf");

			if(stratNum <= 1) {
				r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "f" + futSecs1 + "t([0-9]+)failed", 1), 
						new String[]{prefix + "f" + futSecs1 + "t<level>success", prefix + "f" + futSecs1 + "t<level>failed"}, 
						new String[]{"Request Possible (q_a >= q_r)", "Prefetching Required (q_a < q_r)"}, 
						ResultType.MEAN, "Simulation Time (sec)", "Number of Occurrences", "etc/result_success.pdf",
						"set yrange [0:45000]");
			}
		}

		String suffix = "m" + snapshotTimeSteps.get(0) + "f" + 
				numsSecsLookIntoContextFuture.get(numsSecsLookIntoContextFuture.size() - 1);
		String pref1 = "s" + stratIDs.get(0) + suffix;
		String pref2 = "s" + stratIDs.get(1) + suffix;
		String pref3 = "s" + stratIDs.get(2) + suffix;
		System.out.println(pref1 + "t([0-9]+)resultAgeTotal");
		System.out.println(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)resultAgeTotal", 1));
		r1.createGnuplot(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)resultAgeTotal", 1),
				new String[]{pref1 + "t<level>resultAgeTotal", pref2 + "t<level>resultAgeTotal", pref3 + "t<level>resultAgeTotal"}, 
				new String[]{"Periodic Prefetching (t_p = 900, t_i = 450)",
							"Periodic Prefetching (t_p = 900, t_i = 90)",
							"Context-Based Prefetching (t_p = 900)"},
				ResultType.MEAN, "Simulation Time (sec)", "Accumulated Result Age (sec)", 
				"etc/result_ageTotal.pdf", "set yrange [0:250000]", "set key at -3500,240000"
				);

		System.out.println(pref1 + "t([0-9]+)unusedTotal");
		System.out.println(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)unusedTotal", 1));
		r1.createGnuplot(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)unusedTotal", 1),
				new String[]{pref2 + "t<level>unusedTotal", pref1 + "t<level>unusedTotal"}, 
				new String[]{"Periodic Prefetching (t_p = 900, t_i = 90)",
							"Periodic Prefetching (t_p = 900, t_i = 450)"},
				ResultType.MEAN, "Simulation Time (sec)", "Unused Prefetched Results", 
				"etc/result_unusedTotal.pdf" , "set yrange [0:4000]", "set key at -3500,3800"
				);

		System.out.println(pref1 + "t([0-9]+)unused");
		System.out.println(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)unused", 1));
		r1.createGnuplot(r1.getAllLevelIDsByPattern(pref1 + "t([0-9]+)unused", 1),
				new String[]{pref2 + "t<level>unused", pref1 + "t<level>unused"}, 
				new String[]{"Periodic Prefetching (t_p = 900, t_i = 90)",
							"Periodic Prefetching (t_p = 900, t_i = 450)"},
				ResultType.MEAN, "Simulation Time (sec)", "Unused Prefetched Results", 
				"etc/result_unused.pdf" , "set yrange [0:600]", "set key at -3500,580"
				);

		String prefix = "";
		r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)usage", 1), 
				new String[]{prefix + "t<level>usage"}, 
				new String[]{"Service Data Usage"}, 
				ResultType.MEAN, "Time", "Data Rate (kbps)", "etc/result_usage.pdf");
		String clientID = "1";
		String prefix1 = "e" + clientID;
		String pattern = "(" + prefix1 + ")?t([0-9]+)((linkSpeed)|(usage))";
		r1.createGnuplot(r1.getAllLevelIDsByPattern(pattern, 2),
				new String[]{prefix + "t<level>usage", prefix1 + "t<level>linkSpeed"}, 
				new String[]{"Projected Required Network Quality (q_r)",
					"Estimated Available Network Quality (q_a)"},
				ResultType.MEAN, "Simulation Time (sec)", "Data Rate (kbps)", "etc/result_client1_linkSpeed.pdf",
				"set logscale y", "set yrange [1:1000000]", 
				//"set xrange [2970:*]", "set xtics format '%s%c'" "set xtics rotate by 90 offset 0,-2", "set format x '%.1tK'",
				"set format y '10^{%T}'"
				);

	}

	public static void startSimulation(String serviceURL) throws Exception {

		SimulationTestData.setServiceURL(serviceURL);

		int numCars = 50;
		MovingEntities entities = SimulationTestData.getData(1, numCars);
//		MovingEntities entities = SimulationTestData.getData(29, 29);

		for(MovingEntity ent : entities.entities) {
			dataPoints.addAndGet(ent.path.size());
			double maxTime = ent.path.points.get(ent.path.points.size() - 1).time.time;
			if(maxTime > maxTimePoint.get()) {
				maxTimePoint.set(maxTime);
			}
		}

		test.addEntry("dataPoints", dataPoints.get());

		System.out.println("Starting simulation...");
		System.out.println(maxTimePoint.get());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				test.addEntry("dataPointsGross", dataPointsGross.get());
				double totalTime = System.currentTimeMillis() - startTime;
				LOG.info("Total execution time: " + totalTime);
				test.addEntry("totalRunTime", totalTime);
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
					resultAges.addValue(TimeClock.now(), TimeClock.now() - resultTime);
				} else if(e.type.equals(AuditEvent.E_PREFETCH_MISS)) {
					System.out.println(e); // TODO
					prefetchMisses.addValue(TimeClock.now(), 1);
					//throw new RuntimeException();
				} else if(e.type.equals(AuditEvent.E_UNUSED_RESULT)) {
					resultsUnused.addValue(TimeClock.now(), 1);
				}
			}
		});

		for(PrefetchStrategy s : strategies) {
			for(int futSecs : numsSecsLookIntoContextFuture) {
				failedInvocations.clear();
				successInvocations.clear();
				prefetchHits.clear();
				prefetchMisses.clear();
				resultsUnused.clear();
				resultAges.clear();
				runWithConfig(entities, s, futSecs);
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
		// draw graphs
		boolean createGraphs = false;
		if(createGraphs && new File(resultFile).exists()) {
			createGraphs();
			System.exit(0);
		}
		String serviceURL = "http://" + serviceHost + ":8283/traffic";
		if(startServices) {
			/* deploy services */
			SimulationTestData.deployTestServices(serviceURL);
		}
		if(startClients) {
			startSimulation(serviceURL);
			System.exit(0);
		}
	}

}
