package com.plf.lock.zk;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * @author panlf
 * @date 2023/8/16
 */
public class ZkDistributedLock implements Lock {
    /**
     * 独占排他： znode节点不可重复 自旋锁
     * 阻塞锁：临时序列化节点
     *   1、所有请求要求获取锁时，给每一个请求创建临时序列化节点
     *   2、获取当前节点的前置节点，如果前置节点为空，则获取锁成功，否则监听前置节点
     *   3、获取锁成功之后执行业务操作，然后释放当前节点的锁
     * 可重入：同一线程已经获取过该锁的情况下，可重入
     *   1、在节点的内容中记录服务器、线程以及重入信息
     *   2、ThreadLocal：线程的局部变量，线程私有
     *
     *
     * 1、独占排他互斥使用 节点不重复
     * 2、防死锁
     *      客户端程序获取到锁之后服务器立马宕机。临时节点：一旦客户端服务器宕机，链接就会关闭，
     *          此时zk心跳检测不到客户端程序，删除对应的临时节点。
     *      不可重入：可重入锁
     * 3、防误删：给每一个请求线程创建一个唯一的序列化节点。
     * 4、原子性：
     *      创建节点、删除节点、查询及监听 具备原子性
     * 5、可重入：ThreadLocal实现 节点数据 ConcurrentHashMap
     * 6、自动续期：没有过期时间 也就不需要自动续期
     * 7、单点故障：zk一般都是集群部署
     * 8、zk集群：偏向于一致性集群
     */
    private ZooKeeper zooKeeper;
    private String lockName;

    private String currentNodePath;

    private static final String ROOT_PATH = "/locks";

    private static final ThreadLocal<Integer> THREAD_LOCAL = new ThreadLocal<>();

    public ZkDistributedLock(ZooKeeper zooKeeper, String lockName) {
        this.zooKeeper = zooKeeper;
        this.lockName = lockName;
        try {
            if(zooKeeper.exists(ROOT_PATH,false)==null){
                zooKeeper.create(ROOT_PATH,
                        null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void lock() {
        this.tryLock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        // 创建znode节点过程：为了防止zk客户端程序获取到锁之后，服务器宕机带来的死锁问题
        // 这里创建的是临时节点
        try {
            //判断threadLocal中是否有锁，有锁直接重入（+1）
            Integer flag = THREAD_LOCAL.get();
            if(flag != null && flag > 0){
                THREAD_LOCAL.set(flag+1);
                return true;
            }



            currentNodePath = zooKeeper.create(ROOT_PATH+"/"+lockName+"-",
                    null,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
            String preNode = getPreNode();
            //利用闭锁思想，实现阻塞功能
            if(preNode != null){
                CountDownLatch countDownLatch = new CountDownLatch(1);
                //因为获取前置节点这个操作，不具备原子性，再次判断zk中的前置节点是否存在
                if(zooKeeper.exists(ROOT_PATH + "/" + preNode, watchedEvent -> countDownLatch.countDown()) == null){
                    THREAD_LOCAL.set(1);
                    return true;
                }
                countDownLatch.await();
            }
            THREAD_LOCAL.set(1);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Thread.sleep(80);
                this.tryLock();
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        //删除znode节点的过程
        try {
            THREAD_LOCAL.set(THREAD_LOCAL.get()-1);
            if(THREAD_LOCAL.get() == 0) {
                zooKeeper.delete(currentNodePath, -1);
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    private String getPreNode(){
        try{
            //获取根节点下的所有节点
            List<String> children = zooKeeper.getChildren(ROOT_PATH, false);
            if(CollectionUtils.isEmpty(children)){
                throw new IllegalMonitorStateException("非法操作！");
            }
            //获取和当前节点同一资源的锁
            List<String> nodes = children.stream()
                   .filter(node -> StringUtils.startsWith(node,lockName +"-"))
                   .collect(Collectors.toList());

            if(CollectionUtils.isEmpty(nodes)){
                throw new IllegalMonitorStateException("非法操作！");
            }

            //排队
            Collections.sort(nodes);

            //获取当前节点的下标
            //获取当前节点
            String currentNode = StringUtils.substringAfterLast(currentNodePath,"/");
            int index = Collections.binarySearch(nodes,currentNode);
            if(index < 0){
                throw new IllegalMonitorStateException("非法操作！");
            }else if(index > 0){
                return nodes.get(index - 1);//返回前置节点
            }
            //如果当前节点就是就是第一个节点，则返回null
            return null;
        }catch (Exception e){
            e.printStackTrace();
            throw new IllegalMonitorStateException("非法操作！");
        }
    }
}
