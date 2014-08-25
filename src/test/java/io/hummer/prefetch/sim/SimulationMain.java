package io.hummer.prefetch.sim;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.TestConstants;
import io.hummer.prefetch.PrefetchingService.PrefetchRequest;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.client.PrefetchingCapableClient;
import io.hummer.prefetch.client.ServiceInvocationBuilder;
import io.hummer.prefetch.impl.Context;
import io.hummer.prefetch.impl.TimeClock;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.impl.UsagePattern.ServiceUsage;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntities;
import io.hummer.prefetch.sim.VehicleSimulation.MovingEntity;
import io.hummer.prefetch.sim.VehicleSimulation.PathPoint;
import io.hummer.prefetch.sim.util.AuditEvent;
import io.hummer.prefetch.strategy.PrefetchStrategyContextBased;
import io.hummer.prefetch.strategy.PrefetchStrategyPeriodic;
import io.hummer.util.math.MathUtil;
import io.hummer.util.test.GenericTestResult;
import io.hummer.util.test.GenericTestResult.IterationResult;
import io.hummer.util.test.GenericTestResult.ResultType;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.AtomicDouble;

public class SimulationMain {

	public static void runWithStrategy(MovingEntities ents, 
			PrefetchStrategy strat) throws Exception {
		int startSecs = 5000;
		double stopSecs = 80000; //maxTimePoint.get();
		int stepSecs = 10;
		int lookIntoFutureSecs = 60*15;
		String stratID = strat.getClass().getSimpleName().replace("PrefetchStrategy", "").substring(0, 1);

		/* get service usages */
		List<ServiceUsage> usages = new LinkedList<UsagePattern.ServiceUsage>();
		usages.add(SimulationTestData.getServiceUsage1());
		usages.add(SimulationTestData.getServiceUsage2());
		UsagePattern usageCombined = UsagePattern.combine(usages.toArray(new ServiceUsage[0]));

		/* initialize clients */
		Map<MovingEntity,PrefetchingCapableClient> clients = 
				new HashMap<>();
		for(MovingEntity ent : ents.entities) {
			PrefetchingCapableClient client = 
					new PrefetchingCapableClient(new Context<>());
			clients.put(ent, client);

			/* add subscriptions for service prefetchings */
			for(ServiceUsage usage : usages) {
				if(usage.invocation.prefetchPossible) {
					PrefetchRequest r = new PrefetchRequest();
					r.invocation = usage.invocation;
					r.strategy = strat;
					if(strat instanceof PrefetchStrategyContextBased) {
						r.strategy = new PrefetchStrategyContextBased(
								r.invocation, usage.pattern, 
								lookIntoFutureSecs);
					}
					client.setPrefetchingStrategy(r);
				}
			}
		}

		/* start main loop */
		for(int t = startSecs; t < stopSecs; t += stepSecs) {
			//System.out.println(t);
			int secs = t - startSecs;
			TimeClock.setTime(secs);

			for(int j : Arrays.asList(100, 1000)) {
				if(secs % j == 0) {
					String prefix = "s" + stratID + "m" + j + "t" + secs /* + "i" + (secs / j) +*/;
					test.addEntry(prefix + "hit", prefetchHits.get());
					test.addEntry(prefix + "miss", prefetchMisses.get());
					test.addEntry(prefix + "resultAge",
							new MathUtil().average(TimeClock.
									filterTimestampedValues(resultAges, secs-j, secs)));
				}
			}
			if(firstStrategyIteration.get()) {
				test.addEntryAndRemoveOldIfSame(
					"t[0-9]+usage",
					"t" + secs + "usage",
					usageCombined.predictUsage(secs), result);
				test.addEntry("t" + secs + "failed", failedInvocations.get());
				test.addEntry("t" + secs + "success", successInvocations.get());
			}

			/* move forward */
			for(MovingEntity ent : ents.entities) {
				PathPoint loc = ent.getLocationAtTime(t);
				if(loc != null) {
					PrefetchingCapableClient client = 
							clients.get(ent);
					Context<Object> ctx = client.getContext();
					Map<String,Object> ctxUpdates = new HashMap<>();
					/* set current time */
					ctxUpdates.put(Context.ATTR_TIME, (double)t);
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
					/* set media streaming context */
					ctxUpdates.put(TestConstants.ATTR_MEDIA_ID, "song 1");
					ctxUpdates.put(TestConstants.ATTR_MEDIA_NEXT_CHUNK, "1");
					/* do the context update */
					ctx.setContextAttributes(ctxUpdates);
					System.out.println("coverage at " + loc + ": " + loc.cellNetworkCoverage);
					/* make invocations */
					for(ServiceUsage u : usages) {
						if(u.pattern.predictUsage(t) > 0) {
							try {
								ServiceInvocation inv = u.invocation;
								if(inv instanceof ServiceInvocationBuilder) {
									inv = ((ServiceInvocationBuilder)inv).buildInvocation(ctx);
								}
								client.invoke(inv);
							} catch (IllegalStateException e) {
								/* swallow invocation errors (due to lack of 
								 * network connectivity, which we simulate) */
							}
						}
					}
				}
			}
		}
	}

	static GenericTestResult result = new GenericTestResult();
	static IterationResult test = result.newIteration();
	static final AtomicInteger failedInvocations = new AtomicInteger();
	static final AtomicInteger successInvocations = new AtomicInteger();
	static final AtomicInteger prefetchHits = new AtomicInteger();
	static final AtomicInteger prefetchMisses = new AtomicInteger();
	static final AtomicLong dataPoints = new AtomicLong();
	static final AtomicDouble maxTimePoint = new AtomicDouble();
	static final Map<Double,Double> resultAges = new HashMap<>();
	static final AtomicBoolean firstStrategyIteration = new AtomicBoolean(true);

	public static void main(String[] args) throws Exception {

		String file = SimulationMain.class.getResource("/").getPath() + "/../../etc/sim_result.xml";
		System.out.println(file);
//		boolean createGraphs = false;
//		boolean runTests = true;
		boolean createGraphs = true;
		boolean runTests = false;

		List<? extends PrefetchStrategy> strategies = Arrays.asList(
				new PrefetchStrategyPeriodic(60),
				new PrefetchStrategyContextBased(null, null, 0)
//				, new PrefetchStrategyNone()
				);

		// draw graphs
		if(createGraphs && new File(file).exists()) {
			GenericTestResult r1 = GenericTestResult.load(file);
			for(int j : Arrays.asList(100, 1000)) {
				for(PrefetchStrategy strat : strategies) {
					String stratID = strat.getClass().getSimpleName()
							.replace("PrefetchStrategy", "").substring(0, 1);
					String prefix = "s" + stratID + "m" + j + "";
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)hit", 1), 
							new String[]{prefix + "t<level>hit", prefix + "t<level>miss"},
							new String[]{"Prefetch Hits", "Prefetch Misses"}, 
							ResultType.MEAN, "Time", "Value", "etc/result_" + prefix + "_hitmiss.pdf");
					r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)resultAge", 1), 
							new String[]{prefix + "t<level>resultAge"},
							new String[]{"Prefetched Result Age"}, 
							ResultType.MEAN, "Time", "Seconds", "etc/result_" + prefix + "_age.pdf");
				}
			}
			String prefix = "";
			r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)failed", 1), 
					new String[]{prefix + "t<level>failed", prefix + "t<level>success"}, 
					new String[]{"Failed Invocations", "Successful Invocations"}, 
					ResultType.MEAN, "Time", "Value", "etc/result_success.pdf");
			r1.createGnuplot(r1.getAllLevelIDsByPattern(prefix + "t([0-9]+)usage", 1), 
					new String[]{prefix + "t<level>usage"}, 
					new String[]{"Service Data Usage"}, 
					ResultType.MEAN, "Time", "Data Rate (kbps)", "etc/result_usage.pdf");
			String clientID = "1";
			String prefix1 = "e" + clientID;
			String pattern = "(" + prefix1 + ")?t([0-9]+)((linkSpeed)|(usage))";
			System.out.println(r1.getAllLevelIDsByPattern(pattern, 2).toString().replace(",", "\n"));
			r1.createGnuplot(r1.getAllLevelIDsByPattern(pattern, 2),
					new String[]{prefix + "t<level>usage", prefix1 + "t<level>linkSpeed"}, 
					new String[]{"Service Data Usage", "Estimated Available Link Speed"},
					ResultType.MEAN, "Simulation Time (sec)", "Data Rate (kbps)", "etc/result_client1_usage.pdf",
					"set logscale y", "set yrange [1:1000000]", "set xrange [2970:*]",
					//"set xtics format '%s%c'"
					//"set xtics rotate by 90 offset 0,-2"
					"set format x '%.1tK'",
					"set format y '10^{%T}'"
					);
		}

		if(!runTests) return;

		MovingEntities entities = SimulationTestData.getData();

		for(MovingEntity ent : entities.entities) {
			dataPoints.addAndGet(ent.path.size());
			double maxTime = ent.path.get(ent.path.size() - 1).time.time;
			if(maxTime > maxTimePoint.get()) {
				maxTimePoint.set(maxTime);
			}
		}
		
		test.addEntry("dataPoints", dataPoints.get());

		SimulationTestData.addUsagePatterns(entities);

		System.out.println("Starting simulation...");
		System.out.println(maxTimePoint.get());

		result.saveOnShutdown(file);
		AuditEvent.addListener(".*", new AuditEvent.EventListener() {
			public void notify(AuditEvent e) {
				if(e.type.equals(AuditEvent.E_INV_FAILED)) {
					failedInvocations.incrementAndGet();
				} else if(e.type.equals(AuditEvent.E_INV_SUCCESS)) {
					successInvocations.incrementAndGet();
				} else if(e.type.equals(AuditEvent.E_PREFETCH_HIT)) {
					System.out.println(e); // TODO
					prefetchHits.incrementAndGet();
					@SuppressWarnings("unchecked")
					double resultTime = (double)((Map<String,Object>)e.data).get(Context.ATTR_TIME);
					//System.out.println("age: " + (TimeClock.now() - resultTime));
					resultAges.put(TimeClock.now(), TimeClock.now() - resultTime);
				} else if(e.type.equals(AuditEvent.E_PREFETCH_MISS)) {
					System.out.println(e); // TODO
					prefetchMisses.incrementAndGet();
				}
			}
		});

		/* deploy services */
		SimulationTestData.deployTestServices();

		for(PrefetchStrategy s : strategies) {
			failedInvocations.set(0);
			successInvocations.set(0);
			prefetchHits.set(0);
			prefetchMisses.set(0);
			resultAges.clear();
			runWithStrategy(entities, s);
			firstStrategyIteration.set(false);
			System.gc();
		}

		System.exit(0);
	}

}
