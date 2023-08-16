package com.plf.lock.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 可重入锁
 * @author panlf
 * @date 2023/8/15
 */
public class DistributedRedisLock implements Lock {

    private final StringRedisTemplate stringRedisTemplate;

    private final String lockName;

    private final String uuid;

    private long expire = 30;

    public DistributedRedisLock(StringRedisTemplate stringRedisTemplate, String lockName,String uuid) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockName = lockName;
        this.uuid = uuid + ":" + Thread.currentThread().getId();;
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
        try {
            return this.tryLock(-1L,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 加锁
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
       if(time != -1){
           this.expire = unit.toSeconds(time);
       }

        String script = "if redis.call('exists',KEYS[1]) == 0 or redis.call('hexists',KEYS[1],ARGV[1]) == 1 " +
                "then " +
                "   redis.call('hincrby',KEYS[1],ARGV[1],1) " +
                "   redis.call('expire',KEYS[1],ARGV[2]) " +
                "   return 1 " +
                "else " +
                "   return 0 " +
                "end";

       while(!stringRedisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class),
                Collections.singletonList(lockName),
                uuid,
                String.valueOf(expire))){
            Thread.sleep(50);
       }
         //加锁成功，返回之前，开启定时器自动续期
        this.renewExpire();
        return true;
    }

    /**
     * 解锁
     */
    @Override
    public void unlock() {
        String script = "if redis.call('hexists',KEYS[1],ARGV[1]) == 0 " +
                "then " +
                "   return nil " +
                "elseif  redis.call('hincrby',KEYS[1],ARGV[1],-1) == 0 " +
                "then " +
                "   return redis.call('del',KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";
        Long flag = stringRedisTemplate.execute(new DefaultRedisScript<>(script,Long.class),
                Collections.singletonList(lockName),
                uuid);
        if(flag == null){
            throw new IllegalMonitorStateException("this lock doesn't belong to you!");
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    private void renewExpire(){
        String script = "if redis.call('hexists',KEYS[1],ARGV[1]) == 1 " +
                "then " +
                "   return redis.call('expire',KEYS[1],ARGV[2]) " +
                "else " +
                "   return 0 " +
                "end";
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if(stringRedisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class),
                        Collections.singletonList(lockName),
                        uuid,
                        String.valueOf(expire))){
                    renewExpire();
                }
            }
        },this.expire * 1000 / 3);
    }
}
