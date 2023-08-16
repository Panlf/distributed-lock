package com.plf.lock.zk;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;

/**
 * @author panlf
 * @date 2023/8/16
 */
@Component
public class ZkClient {
    private ZooKeeper zooKeeper;

    @PostConstruct
    public void init(){
        //获取zk连接
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try{
            zooKeeper = new ZooKeeper("localhost:2181", 3000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    Event.KeeperState state =watchedEvent.getState();
                    if(Event.KeeperState.SyncConnected.equals("state") &&
                    Event.EventType.None.equals(watchedEvent.getType())){
                        System.out.println("获取到了连接..."+watchedEvent);
                        countDownLatch.countDown();
                    }else if(Event.KeeperState.Disconnected.equals(state)){
                        System.out.println("关闭连接...");
                    }
                }
            });
            countDownLatch.await();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy(){
        //释放zk
        try{
            if(zooKeeper != null){
                zooKeeper.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public ZkDistributedLock getLock(String lockName){
        return new ZkDistributedLock(zooKeeper,lockName);
    }
}
