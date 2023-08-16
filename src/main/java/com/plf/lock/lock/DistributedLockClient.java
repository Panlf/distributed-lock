package com.plf.lock.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * @author panlf
 * @date 2023/8/15
 */
@Component
public class DistributedLockClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String uuid;

    public DistributedLockClient(){
        this.uuid = UUID.randomUUID().toString();
    }

    public DistributedRedisLock getRedisLock(String lockName){
        return new DistributedRedisLock(stringRedisTemplate,lockName,uuid);
    }
}
