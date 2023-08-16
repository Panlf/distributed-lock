package com.plf.lock.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author panlf
 * @date 2023/8/16
 */
@Configuration
public class CuratorConfig {

    /**
     *  1、InterProcessMutex 类似于 ReentrantLock可重入锁
     *      public InterProcessMutex(CuratorFramework client,String path)
     *      public void acquire()
     *      public void release()
     *
     *      InterProcessMutex
     *          basePath 初始化锁时指定节点路径
     *          internals LockInternals对象，加锁 解锁
     *          ConcurrentMap<Thread,LockData> threadData 记录了重入信息
     *          class LockData {
     *              Thread lockPath lockCount
     *          }
     *
     *      LockInternals
     *          maxLeases： 租约，值为1
     *          basePath： 初始化锁时指定的节点路径
     *          path：basePath + "/lock-"
     *
     *       加锁
     *          InterProcessMutex.acquire() --> InterProcessMutex.internalLock() -->
     *              LockInternals.attemptLock()
     *
     * 2、InterProcessSemaphoreMutex 不可重入锁
     *
     * 3、InterProcessReadWriteMutex 可重入的读写锁
     *      读读可以并发的
     *      读写不可以并发
     *      写写不可以并发
     *      写锁在释放之前会阻塞请求线程，而读写是不会的。
     *
     * 4、InterProcessMultiLock 联锁 redisson中的联锁对象
     *
     * 5、InterProcessSemaphoreV2：信号量 限流
     *
     * 6、共享计数器
     *      ShareCount
     *      DistributedAtomicNumber
     *          DistributedAtomicLong
     *          DistributedAtomicInteger
     */


    @Bean
    public CuratorFramework curatorFramework(){
        //初始化一个重试策略。这里使用的指数补偿策略。初始间隔时间，重试次数
        ExponentialBackoffRetry retry = new ExponentialBackoffRetry(10000, 3);;
        //初始化curator客户端
        CuratorFramework newClient = CuratorFrameworkFactory.newClient("localhost:2181", retry);
        newClient.start();//手动启动
        return newClient;
    }
}
