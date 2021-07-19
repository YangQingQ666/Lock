package cn.mpy.testredislock.controller;

import cn.mpy.testredislock.config.RedisLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @Autowired
    private RedisLock redisLock;
    @GetMapping("/test")
    public void test(){
        String token = null;
            try{
                token = redisLock.lock("lock_name", 10000, 11000);
                if(token != null) {
                    System.out.println("我拿到了锁哦");
                    // 执行业务代码
                } else {
                    System.out.println("我没有拿到锁唉");
                }
            } finally {
                if(token!=null) {
                    redisLock.unlock("lock_name", token);
                }
            }
    }
}
