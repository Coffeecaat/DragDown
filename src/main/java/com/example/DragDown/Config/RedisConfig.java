package com.example.DragDown.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> joinRoomScript(){
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();


        redisScript.setLocation(new ClassPathResource("scripts/join_room.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean
    public RedisScript<Long> leaveRoomScript(){
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/leave_room.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
