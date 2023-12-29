import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.{File, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger

object ProducerConsumerMonitor {

  class Monitor {
    private val buffer: mutable.Queue[Int] = mutable.Queue[Int]()
    private val capacity: Int = 100
    private val file: File = new File("Numbers.txt")
    private val writer: PrintWriter = new PrintWriter(file)
    private val consumerCounter: AtomicInteger = new AtomicInteger(1)

    // Producer adds numbers to the buffer
    def produce(numbers: Seq[Int]): Unit = synchronized {
      numbers.foreach { number =>
        while (buffer.size >= capacity) {
          println("Buffer is full. Producer is waiting...")
          wait()
        }
        buffer.enqueue(number)
        println(s"Produced: $number")
        notifyAll()
      }
    }

    // Consumer fetches numbers from the buffer and writes to the file
    def consume(): Unit = synchronized {
      while (buffer.isEmpty) {
        println("Buffer is empty. Consumer is waiting...")
        wait()
      }
      val number = buffer.dequeue()
      val consumerId = consumerCounter.getAndIncrement()
      writer.println(s"Consumer $consumerId consumed: $number")
      writer.flush() // Ensure the content is flushed to the file
      println(s"Consumer $consumerId consumed: $number")
      notifyAll()
    }

    // Close file writer when all consumer threads are done
    def closeFile(): Unit = synchronized {
      writer.close()
    }
  }

  def main(args: Array[String]): Unit = {
    val monitor = new Monitor
    val producers = List.fill(5)(Future {
      val randomNumbers = (1 to 50).map(_ => scala.util.Random.nextInt(100) + 1)
      monitor.produce(randomNumbers)
    })

    val consumers = List.fill(5)(Future {
      while (true) {
        monitor.consume()
      }
    })

    // Wait for all producer and consumer threads to complete
    val allThreads = producers ++ consumers
    Future.sequence(allThreads).onComplete { _ =>
      monitor.closeFile()
      println("All threads completed.")
    }
  }
}
