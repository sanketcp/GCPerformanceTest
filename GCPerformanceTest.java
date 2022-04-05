import java.lang.Thread;
import java.lang.Object;

class MyObject {
    private long[] obj = null;
    public MyObject next = null;
    public MyObject prev = null;
    public MyObject(int objSize) {
        obj = new long[objSize*128]; // 128*8=1024 bytes
        for (int i=0; i<objSize*128; i++) {
            obj[i] = i/2+i/3+i/4+i/5; // some work load
        }
    }
}


class GCPerformanceThread extends Thread {
    private Thread t;
    private int threadNum;

    //Config Parameters
    private int objSize;
    private int baseSize;  // # of objects in the base
    private int chunkSize; // # of objects in chunk
    private int waitTime;      // Wait time in milliseconds
    private int cpuTime;   // CPU time in milliseconds
    private int warmupLoops;   // warmup loops: 256*32 = 8GB
    private int runLoops;
    private Result result;

    //Used for internal processing members
    private long myCounter;
    private long totCpuTime;
    private long intCpuTime;

    MyObject head = null;
    MyObject tail = null;

    GCPerformanceThread(int myThreadNum, int myobjSize, int mybaseSize, int myChunkSize, int myCPU, int myWait, int myWarmup, int myRunLoops, Result myResult)
    {
        threadNum = myThreadNum;

        objSize = myobjSize;
        baseSize = mybaseSize;
        chunkSize = myChunkSize;
        cpuTime = myCPU;
        waitTime = myWait;
        warmupLoops = myWarmup;
        runLoops = myRunLoops;
        result = myResult;

        //Used for internal processing members
        intCpuTime = 0;
        totCpuTime = 0;
        myCounter = 0;
    }

    void add(MyObject o) {
        if (head==null) {
            head = o;
            tail = o;
        } else {
            o.prev = head;
            head.next = o;
            head = o;
        }
    }

    void removeTail() {
      	if (tail!=null) {
      	    if (tail.next==null) {
      	       tail = null;
      	       head = null;
      	    } else {
      	       tail = tail.next;
      	       tail.prev = null;
      	    }
      	}
    }

   void burnCPUAndSleep() {
      try {
          int fakeCounter=0;
          long start = System.currentTimeMillis();
          //Execute till cpuTime, then wait for waitTime. Record actual CPU time
          while(true) {
			myCounter++;
			for (int i=0; i < 100000; i++) { fakeCounter++; }
            long end = System.currentTimeMillis();
            if((end-start) > cpuTime) {
               intCpuTime = (end-start);
               totCpuTime += intCpuTime;
               break;
            }
          }
          Thread.sleep(waitTime);
      } catch (InterruptedException e) {
         System.out.println("Interreupted...");
      }
   }

   public void run() {
      t = Thread.currentThread();
      // Prepare base queue
      for (int m=0; m<baseSize; m++) {
         add(new MyObject(objSize));
      }

      // Warm-up phase
      for (int k=0; k<warmupLoops; k++) {
         for (int m=0; m<chunkSize; m++) {
            add(new MyObject(objSize));
         }
         for (int m=0; m<chunkSize; m++) {
            removeTail();
         }
      }

      long dt0 = System.currentTimeMillis();
      long dt1 = dt0;
      double minPerf = 999999;
      double maxPerf = 0;
      long dt,de;
      long tempMyCounter=0;

      Runtime rt = Runtime.getRuntime();

      for (int r=0; r < runLoops; r++) {
         for (int m=0; m<chunkSize; m++) {
            add(new MyObject(objSize));
         }
         for (int m=0; m<chunkSize; m++) {
            removeTail();
         }
         burnCPUAndSleep();

         long dt2 = System.currentTimeMillis();

         dt = dt2 - dt1;
         de = dt - (cpuTime+waitTime);
         double perf = (1000*chunkSize)/(dt2-dt1-(cpuTime+waitTime)); // per second
         if (perf<minPerf) minPerf = perf;
         if (perf>maxPerf) maxPerf = perf;
         dt1 = dt2;

         long tm = rt.totalMemory()/1024;
         long fm = rt.freeMemory()/1024;
         System.out.format("%-10s%-10s%-15s%-15s%-15s%-15s%-15s%-10s%-10s%-10s%-10s%-15s%-15s%-10s%-10s\n",r
            ,"| "+t.getId()
            ,"| "+(dt)                 // Real time in millis
            ,"| "+(de)                 // Execution time in millis
            ,"| "+(intCpuTime)
            ,"| "+(waitTime)
            ,"| "+(myCounter-tempMyCounter)
            ,"| "+String.format("%.2f",(1000000/minPerf))   // Latency (ms/1000 objects)
            ,"| "+String.format("%.2f",((double)(1000*chunkSize)/de)) // Average throughput
            ,"| "+String.format("%.2f",minPerf)              // Best throughput
            ,"| "+String.format("%.2f",maxPerf)              // Worst throughput
            ,"| "+(chunkSize) // Objects processed
            ,"| "+String.format("%.2f",((double)(objSize*chunkSize)/(de*1.024))) //Allocation rate
            ,"| "+tm
            ,"| "+fm);
         tempMyCounter=myCounter;
      }

      dt = dt1 - dt0;
      de = dt - (runLoops*(cpuTime+waitTime));
      long tm = rt.totalMemory()/1024;
      long fm = rt.freeMemory()/1024;

      System.out.format("%-10s%-10s%-15s%-15s%-15s%-15s%-15s%-10s%-10s%-10s%-10s%-15s%-15s%-10s%-10s\n","Total"
            ,"| "+t.getId()
            ,"| "+(dt)      // Real time in millis
            ,"| "+(de)                 // Execution time in millis
            ,"| "+(totCpuTime)
            ,"| "+(waitTime*runLoops)
            ,"| "+(myCounter)
            ,"| "+String.format("%.2f",(1000000/minPerf))   // Latency (ms/1000 objects)
            ,"| "+String.format("%.2f",((double)(1000*runLoops*chunkSize)/de)) // Average throughput
            ,"| "+String.format("%.2f",minPerf)              // Best throughput
            ,"| "+String.format("%.2f",maxPerf)              // Worst throughput
            ,"| "+(runLoops*chunkSize) // Objects processed
            ,"| "+String.format("%.2f",((double)(objSize*runLoops*chunkSize)/(de*1.024))) //Allocation rate
            ,"| "+tm
            ,"| "+fm);
       result.storeResult(dt, de, totCpuTime, myCounter, (1000000/minPerf), ((1000*runLoops*chunkSize)/de), minPerf, maxPerf, (runLoops*chunkSize), ((objSize*runLoops*chunkSize)/(de*1.024)));
   }
}

class Result {
    int threadNum;
    long wallTime;
    long execTime;
    long cpuTime;
    long myCounter;
    double latency;
    double avgThroughput;
    double maxThroughput;
    double minThroughput;
    long objProcessed;
    double allocRate;

    Result(int myThreadNum)
    {
        threadNum = myThreadNum;
        wallTime = 0;
        execTime = 0;
        cpuTime = 0;
        myCounter = 0;
        latency = 0;
        avgThroughput = 0;
        maxThroughput = 0;
        minThroughput = 0;
        objProcessed = 0;
        allocRate = 0;
    }

    void storeResult(long wall, long exec, long cpu, long myC, double lat, double avgThr, double maxThr, double minThr, long objP, double allocR)
    {
        wallTime = wall;
        execTime = exec;
        cpuTime = cpu;
        myCounter = myC;
        latency = lat;
        avgThroughput = avgThr;
        maxThroughput = maxThr;
        minThroughput = minThr;
        objProcessed = objP;
        allocRate = allocR;
    }
}

//java GCPerformanceTest [runLoopsLoops] [numThreads] [cpuTime] [waitTime] [objSize] [baseSize] [chunkSize] [warmUpLoops]
class GCPerformanceTest {

   public static void main(String[] arg) {
       int runLoops = 60;   // runLoops loops
       int numThreads = 1; // number of Threads
       int objSize = 1024; // in KB, default = 1 MB
       int baseSize = 32;  // # of objects in the base
       int chunkSize = 32; // # of objects in chunk
       int waitTime = 3000;    // in milliseconds: 1 second
       int cpuTime = 700;
       int warmupLoops = 0;   // warmup loops

       if (arg.length>0) runLoops = Integer.parseInt(arg[0]);
       if (arg.length>1) numThreads = Integer.parseInt(arg[1]);
       if (arg.length>2) cpuTime = Integer.parseInt(arg[2]);
       if (arg.length>3) waitTime = Integer.parseInt(arg[3]);
       if (arg.length>4) objSize = Integer.parseInt(arg[4]);
       if (arg.length>5) baseSize = Integer.parseInt(arg[5]);
       if (arg.length>6) chunkSize = Integer.parseInt(arg[6]);
       if (arg.length>7) warmupLoops = Integer.parseInt(arg[7]);

       System.out.println("Parameters:\n"+"RunTime Loops="+runLoops+", Threads="+numThreads
          +", cpuTime="+cpuTime+" ms,waitTime="+waitTime+" ms"
          +", Size="+objSize+"KB"+", Base="+baseSize +" objects, Chunk="+chunkSize+" objects"
          +", Wait="+waitTime+"ms" +", Warmup="+warmupLoops);
       System.out.println();

       GCPerformanceThread [] gcPerformanceThreads;
       gcPerformanceThreads = new GCPerformanceThread[numThreads];
       Result [] results;
       results = new Result[numThreads];

       System.out.format("%-10s%-10s%-15s%-15s%-15s%-15s%-15s%-10s%-10s%-10s%-10s%-15s%-15s%-10s%-10s\n","","","|","","Time(ms)","","","|","","Throughput","","|Objects","","|Memory(KB)","");
       System.out.format("%-10s%-10s%-15s%-15s%-15s%-15s%-15s%-10s%-10s%-10s%-10s%-15s%-15s%-10s%-10s\n","Loop#","| Tid","| Wall Time","| Exec Time","| CPU Time","| Wait Time","| Computes","| Latency","| Avg","| Max","| Min","| Processed","| Alloc(MB/s)","| Total","|Free");

       for (int threads = 0; threads < numThreads; threads++)
       {
           results[threads] = new Result(threads);
           gcPerformanceThreads[threads] = new GCPerformanceThread(threads, objSize, baseSize, chunkSize, cpuTime, waitTime, warmupLoops, runLoops, results[threads]);
           gcPerformanceThreads[threads].start();
       }

       try {
           for (int threads = 0; threads < numThreads; threads++)
           {
               gcPerformanceThreads[threads].join();
           }
       } catch (InterruptedException e) {
           e.printStackTrace();
       }

       //Print Summary
       long wallTimeMax=0;
       double execTimeSum=0;
       double cpuTimeSum=0;
       long myCounterSum=0;
       double latencySum=0;
       double avgThroughputSum=0;
       double maxThroughput=0;
       double minThroughput=100000;
       double objProcessedSum=0;
       double allocRateSum=0;

       for (int threads = 0; threads < numThreads; threads++)
       {
           if (results[threads].wallTime > wallTimeMax) wallTimeMax=results[threads].wallTime;
           execTimeSum += results[threads].execTime;
           cpuTimeSum += results[threads].cpuTime;
           myCounterSum += results[threads].myCounter;
           latencySum  += results[threads].latency;
           avgThroughputSum += results[threads].avgThroughput;
           if (results[threads].maxThroughput > maxThroughput) maxThroughput = results[threads].maxThroughput;
           if (results[threads].minThroughput < minThroughput) minThroughput = results[threads].minThroughput;
           objProcessedSum += results[threads].objProcessed;
           allocRateSum += results[threads].allocRate;
       }
       System.out.format("%-10s%-10s%-15s%-15s%-15s%-15s%-15s%-10s%-10s%-10s%-10s%-15s%-15s%-10s%-10s\n","Summary"
            ,"| "
            ,"| "+(wallTimeMax)      // Real time in millis
            ,"| "+String.format("%.2f",(execTimeSum))                 // Execution time in millis
            ,"| "+String.format("%.2f",(cpuTimeSum))                 // Execution time in millis
            ,"| "+(waitTime*numThreads*runLoops)                 // Execution time in millis
            ,"| "+(myCounterSum)  //Total computations
            ,"| "+String.format("%.2f",(latencySum/numThreads))   // Latency (ms/1000 objects)
            ,"| "+String.format("%.2f",(avgThroughputSum/numThreads)) // Average throughput
            ,"| "+String.format("%.2f",(maxThroughput)) // Best throughput
            ,"| "+String.format("%.2f",(minThroughput)) // Worst throughput
            ,"| "+String.format("%.2f",(objProcessedSum)) // Objects processed
            ,"| "+String.format("%.2f",(allocRateSum)) //Allocation rate
            ,"| "
            ,"| ");
   }
}
