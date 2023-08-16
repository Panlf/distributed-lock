package com.plf.lock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.plf.lock.bean.Stock;
import com.plf.lock.lock.DistributedLockClient;
import com.plf.lock.lock.DistributedRedisLock;
import com.plf.lock.mapper.StockMapper;
import com.plf.lock.service.StockService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author panlf
 * @date 2023/8/1
 */
@Service
//@Scope(value = "prototype",proxyMode = ScopedProxyMode.TARGET_CLASS)
public class StockServiceImpl
        extends ServiceImpl<StockMapper, Stock>
        implements StockService {

    @Resource
    private StockMapper stockMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ReentrantLock reentrantLock = new ReentrantLock();

    @Resource
    private DistributedLockClient distributedLockClient;

    @Resource
    private RedissonClient redissonClient;


    /**
     * redisson：redis的java客户端，分布式锁
     *      使用：
     *       可重入锁RLock对象 CompletableFuture + lua脚本 + hash
     *          RLock lock = redissonClient.getLock("");
     *          lock.lock()/unlock
     *
     *       公平锁
     *          RLock lock = redissonClient.getFairLock("")
     *          lock.lock()/unlock
     *
     *       联锁 和 红锁
     *
     *       读写锁
     *          RReadWriteLock rwLock = redissonClient.getReadWriteLock("")
     *          rwLock.readLock().lock()/unlock()
     *          rwLock.writeLock().lock()/unlock()
     *
     *       信号量
     *           RSemaphore semaphore = redissonClient.getSemaphore("")
     *           semaphore.trySetPermits(3)
     *           semaphore.acquire()/release()
     *
     *       闭锁
     *          RCountDownLatch cdl = redissonClient.getCountDownLatch("")
     *          cdl.trySetCount(6)
     *          cdl.await()/countDown();
     */
    public void deduct(){
        RLock lock = redissonClient.getLock("lock");
        lock.lock();
        try {
            //1、查询库存信息
            String stock = stringRedisTemplate.opsForValue().get("stock");
            //2、判断库存是否充足
            if(stock != null && stock.length() != 0){
                int st = Integer.parseInt(stock);
                if(st > 0){
                    //3、扣减库存
                    stringRedisTemplate.opsForValue().set("stock",String.valueOf(--st));
                }
            }

        }finally {
            lock.unlock();
        }
    }

    public void deduct06(){
        DistributedRedisLock redisLock = distributedLockClient.getRedisLock("lock");
        redisLock.lock();

        try {
            //1、查询库存信息
            String stock = stringRedisTemplate.opsForValue().get("stock");
            //2、判断库存是否充足
            if(stock != null && stock.length() != 0){
                int st = Integer.parseInt(stock);
                if(st > 0){
                    //3、扣减库存
                    stringRedisTemplate.opsForValue().set("stock",String.valueOf(--st));
                }
            }

        }finally {
            redisLock.unlock();
        }

    }

    public void deduct05(){
        String uuid = UUID.randomUUID().toString();
        while(!stringRedisTemplate.opsForValue().setIfAbsent("lock",uuid,3,TimeUnit.SECONDS)){
            try{
                Thread.sleep(50);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        //加锁setnx
        //boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock","111");
        //重试：递归调用
        /*
        if(!lock){
            try{
                Thread.sleep(50);
                this.deduct();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        */
        try{
            //stringRedisTemplate.expire("lock",3, TimeUnit.SECONDS);
            //1、查询库存信息
            String stock = stringRedisTemplate.opsForValue().get("stock");
            //2、判断库存是否充足
            if(stock != null && stock.length() != 0){
                int st = Integer.parseInt(stock);
                if(st > 0){
                    //3、扣减库存
                    stringRedisTemplate.opsForValue().set("stock",String.valueOf(--st));
                }
            }
        } finally {
            //先判断是否自己的锁，再解锁
            if(StringUtils.equals(stringRedisTemplate.opsForValue().get("lock"),uuid)){
                stringRedisTemplate.delete("lock");
            }
        }
    }

    /**
     * redis
     *    1、jvm本地锁机制
     *
     *    2、redis乐观锁：
     *      watch 可以监控一个或者多个key的值，如果在事务（exec）执行之前，key的值发生变化则取消事务执行
     *      multi 开始事务
     *      exec  执行事务
     *      性能很低、连接数不够用
     *    3、分布式锁
     *        跨进程、跨服务、跨服务器
     *
     *       实现方式
     *       1、基于redis实现
     *       2、基于zookeeper/etcd实现
     *       3、基于mysql实现
     *
     *       特征
     *       1、独占排他使用 setnx
     *       2、防死锁
     *          如果redis客户端程序从redis服务中获取到锁之后立马宕机。
     *          解决：给锁添加过期时间。expire
     *       3、原子性
     *          获取锁和过期时间之间：set key value ex 3 nx
     *       4、防误删：解铃还需系铃人
     *          先删除
     *       5、自动续期
     *
     *
     *       操作
     *          1、加锁 setnx
     *          2、解锁 del
     *          3、重试： 递归 循环
     *
     *  RedLock算法
     *  1、应用程序获取系统当前时间
     *  2、应用程序使用相同的kv值依次从多个redis实例中获取锁。如果某一个节点超过一定时间依然没有获取到锁则直接放弃，
     *  尽快尝试从下一个健康的redis节点获取锁，以避免被一个宕机了的节点阻塞
     *  3、计算获取锁的消耗时间 = 客户端程序的系统当前时间 - step1中的时间。获取锁的消耗时间小于总的锁定时间（30s）并且
     *  半数以上节点获取锁成功，认为获取锁成功
     *  4、计算剩余锁定时间 = 总的锁定时间 - step3中的消耗时间
     *  5、如果获取锁失败了，对所有的redis节点释放锁。
     *
     */
    public void deduct04(){
        stringRedisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations redisOperations) throws DataAccessException {
                redisOperations.watch("stock");
                String stock = redisOperations.opsForValue().get("stock").toString();
                if (stock != null && stock.length() > 0) {
                    int value = Integer.parseInt(stock);
                    if (value > 0) {
                        redisOperations.multi();
                        redisOperations.opsForValue().set("stock", String.valueOf(--value));
                        //执行事务
                        List exec = redisOperations.exec();
                        //如果执行事务的返回结果集为空，则表示减库存失败，重试
                        if(CollectionUtils.isEmpty(exec)){
                            try {
                                Thread.sleep(40);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            deduct();
                        }
                        return exec;
                    }
                }
                return null;
            }
        });
    }

    //@Transactional
    public void deduct03(){
        //查询库存信息并锁定库存信息
        List<Stock> stocks = stockMapper.selectList(new QueryWrapper<Stock>().eq("id",1));
        //选定第一个库存
        Stock stock = stocks.get(0);
        if(stock != null && stock.getCount() > 0){
            stock.setCount(stock.getCount() - 1);
            Long version =stock.getVersion();
            stock.setVersion(version+1);
            if(stockMapper.update(stock,new UpdateWrapper<Stock>()
                    .eq("id",1)
                    .eq("version",version))==0){
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //更新失败则重试
                this.deduct();
            }
        }
    }


    @Transactional
    public void deduct02(){
        List<Stock> stocks = stockMapper.queryStock(1);
        Stock stock = stocks.get(0);
        if(stock != null && stock.getCount() > 0){
            stock.setCount(stock.getCount() - 1);
            stockMapper.updateById(stock);
        }
    }

    //@Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void deduct01() {
        /*reentrantLock.lock();
        try {
            Stock stock = stockMapper.selectOne(new QueryWrapper<Stock>().eq("id", "1"));
            if (stock != null && stock.getCount() > 0) {
                stock.setCount(stock.getCount() - 1);
                stockMapper.updateById(stock);
            }
        }finally {
            reentrantLock.unlock();
        }*/
        //update insert delete 写操作本身就会加锁
        stockMapper.updateStock(1,1);
    }

    /**
     * JVM本地锁失效
     * 1、多例模式
     * 2、事务 READ_UNCOMMITTED可以解决这个问题，但是不建议使用
     * 3、集群部署
     *
     * 一个SQL语句：更新数量时判断
     * 解决：三个锁失效
     * 问题
     *  1、锁范围问题
     *  2、同一个商品有多条库存记录
     *  3、无法记录库存变化前后的状态
     *
     * 悲观锁 select ... for update
     * 问题
     *    1、性能问题
     *    2、死锁问题：对多条数据加锁时，加锁顺序要一致
     *    3、库存操作要统一：select...for update 普通select
     * mysql悲观锁中使用行级锁：
     *  1、锁的查询或者更新条件必须是索引字段
     *  2、查询或者更新条件必须是具体值
     *
     * 乐观锁
     *   时间戳 version版本号 CAS机制
     *   问题：
     *    1、高并发情况下，性能极低
     *    2、ABA问题
     *    3、读写分离情况下导致乐观锁不可靠
     */

    /**
     * MySQL锁总结
     * 性能 一个SQL > 悲观锁 > JVM锁 > 乐观锁
     *
     * 如果追求极致性能、业务场景简单并且不需要记录数据前后变化的情况下。
     * 优先选择：一个SQL
     *
     * 如果写并发量较低（多读），争抢不是很激烈的情况下优先选择：乐观锁
     * 如果写并发量较高， 一般会经常冲突，此时选择乐观锁的话，会导致业务代码不间断重试。
     * 优先选择：mysql悲观锁
     *
     * 不推荐JVM本地锁。
     *
     *
     * 基于MySQL的分布式锁
     * 	思路
     * 		1、加锁：insert into tb_lock(lock_name) values('lock') 执行成功代表获取锁成功
     * 		2、释放锁：获取锁成功的请求执行业务操作，执行完成之后通过delete删除对应记录
     * 		3、重试：递归
     * tb_lock	（id、lock_name、lock_time、server_id、thread_id、count）
     *
     * 1、独占排他互斥使用 唯一键索引
     * 2、防死锁
     * 	客户端程序获取到锁之后，客户端程序的服务器宕机。给锁记录添加一个获取锁时间列。
     * 		额外的定时器检查获取锁的系统时间和当前系统时间的差值是否超过了阈值。
     * 	不可重入：可重入 记录服务信息 及 线程信息 重入次数
     * 3、防误删：借助ID唯一性防止误删
     * 4、原子性：一个写操作 还可以借助于mysql悲观锁
     * 5、可重入
     * 6、自动续期：服务器内的定时器重置获取锁的系统时间
     * 7、单机故障，搭建mysql主备
     * 8、集群情况下锁机制失效问题。
     * 9、阻塞锁
     */

    /**
     * 总结
     * 1、简易程序 mysql > redis(lua脚本) > zk
     * 2、性能	redis > zk > mysql
     * 3、可靠性 zk > redis = mysql
     * 追求极致性能 redis
     * 追求可靠性 zk
     */
}
