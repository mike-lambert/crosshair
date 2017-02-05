package ru.cyberspacelabs.threaded;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by mzakharov on 03.02.17.
 */
public abstract class Threaded implements Runnable{
    private boolean running;
    protected Thread worker;
    protected static AtomicLong threadCounter = new AtomicLong(0);

    @Override
    public void run() {
        while(running){
            try {
                doWorkCycle();
            } catch (Exception e){
                e.printStackTrace();
                stop();
            }
        }
    }

    public synchronized void stop() {
        running = false;
        if (worker != null){
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        worker = null;
        doAfterStop();
    }

    protected abstract void doAfterStop();

    public synchronized void start() throws Exception{
        if (!isRunning()){
            running = true;
            doBeforeStart();
            worker = new Thread(this);
            worker.setName(this.getClass().getSimpleName() + "-Worker::" + threadCounter.incrementAndGet());
            worker.setDaemon(true);
            worker.start();
        }
    }

    protected abstract void doBeforeStart() throws Exception;

    public boolean isRunning(){
        return worker != null && worker.isAlive();
    }

    protected abstract void doWorkCycle();
}