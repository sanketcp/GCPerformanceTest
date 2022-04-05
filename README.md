# GCPerformanceTest
A simple test to create flavors of Garbage Collection activities and application thread loads for measuring JVM performance

===============================================================================================================================================================
Program Details:
- Program first creates a LinkedList object (1 per thread)
- It creates a base list (Number of objects in the list). Allocates objects mentioned in the "Base Size" param
- In every runTime loop, program perform following actions per thread
    * Allocate/Deallocate objects from the List, equal to chunk size configured
    * Burn some CPU
    * Sleep
- Warmup Loop is optional, where per thread performs allocation/deallocation of objects equal to the chunk size

You can create different scenarios by configuring Threads/Object Size/Chunk Size/CPU Burn Time/Wait Time.

===============================================================================================================================================================

Usage:

- Run with the flavor of JVM for which you want to test the performance
java GCPerformanceTest [runLoopsLoops] [numThreads] [cpuTime] [waitTime] [objSize] [baseSize] [chunkSize] [warmUpLoops]

E.g.
usr/java/jre1.8.0_45/bin/java -Xms400g  -Xmx400g  -XX:InitialCodeCacheSize=512m -XX:ReservedCodeCacheSize=768m  -XX:ThreadStackSize=1280 -XX:+PrintGCDateStamps -verbose:gc -Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintReferenceGC GCPerformanceTest 12 2 700 300 1024 1024 128 0

12 => Number of runTime object allocation loops
2 => Number of threads
700 => CPU Burn Time per loop (in ms)
300 => Wait time per loop (in ms)
1024 => Object size to allocate (in KB)
1024 => Base size of the list (number of objects)
128 => Chunk Size (Number of objects to allocate/deallocate per loop)
0 => Number of warmup object allocation loops

===============================================================================================================================================================

Output:

* For Every Runtime Loop, program prints per thread
- Loop#
- ThreadID 
- Wall Time for the loop
- Total time spent in object allocation/deallocation (Exec Time)
- Total CPU time spent by USER threads doing computation
- Total Wait time by USER threads
- Total Number of Computations by the Thread
- Latency in (ms): Time to allocate 1000 objects
- Throughput: Object allocations per second (Avg, min, max)
- Object Processed so far
- Object Allocation Rate (MB/sec)
- Total Heap Memory
- Total Free Memory
* Summary Per Thread for all Loops (Sum for metrics printing "Total", Average for metrics printing "Average")
* Combined Summary for all threads (Sum for metrics printing "Total", Average for metrics printing "Average")  

To get more details, please generate GC log file which can be later analyzed using GCEasy/GCLogAnalyzer

===============================================================================================================================================================

