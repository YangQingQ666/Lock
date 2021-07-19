package cn.mpy.testredislock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SaleTikets implements Runnable {
    private int tickCount=10;//总的票数，这里是共享资源
    Object mutex =new Object();//锁，自己定义的，或者实例的的锁
    public void saleTiket(){
        // 用同步代码块进行包围起来,执行里面的代码需要mutex的锁，但是mutex只有一个锁。
        // 这样在执行时,只能有一个线程执行同步代码块里面的内容
        synchronized(mutex){
            if (tickCount>0){
                tickCount--;
                System.out.println(Thread.currentThread().getName()+"正在买票，还剩余"+tickCount+"张票");
            }else{
                System.out.println("票没啦");
                return;
            }
        }
    }
    public void run() {
        while (tickCount>0){
            saleTiket();
            /**
             * 在同步代码块里面睡觉,和不睡效果是一样 的,作用只是自已不执行,也不让线程执行。sleep不释放锁，抱着锁睡觉。其他线程拿不到锁，也不能执行同步代码。wait()可以释放锁
             * 所以把睡觉放到同步代码块的外面,这样卖完一张票就睡一会,让其他线程再卖,这样所有的线程都可以卖票
             */
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        final SaleTikets saleTikets = new SaleTikets();

        // 构造一个线程池
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue(2),
                new ThreadPoolExecutor.DiscardOldestPolicy());

        threadPoolExecutor.execute(() -> {
            try {
                // 执行 方法
                saleTikets.run();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 关闭线程池
                threadPoolExecutor.shutdown();
            }
        });
    }
}
