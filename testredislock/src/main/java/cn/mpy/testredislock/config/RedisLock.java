package cn.mpy.testredislock.config;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;

import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisLock {

    /**
     * 解锁脚本(lua)，原子操作
     * 用上面生成的 value 和redis里面获取的 value 进行比较，相同则表示是相同的客户端，之后释放锁即可。
     * 这里的问题是分为两步操作，不是原子性的，当第一步执行完成后锁可能因为过期销毁，就可能会发生另一个客户端刚拿到锁就被释放的问题。
     * 最好的解决方式是通过 redis 的 eval() 方法，这个方法可以执行一段脚本，脚本运行是原子性的，在脚本运行期间没有客户端可以操作，下面为拷贝过来的代码
     */
    private static final String unlockScript =
            "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n"
                    + "then\n"
                    + "    return redis.call(\"del\",KEYS[1])\n"
                    + "else\n"
                    + "    return 0\n"
                    + "end";

    private StringRedisTemplate redisTemplate;

    public RedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 加锁，有阻塞
     * @param name
     * @param expire
     * @param timeout
     * @return
     */
    public String lock(String name, long expire, long timeout){
        long startTime = System.currentTimeMillis();
        String token;
        do{
            token = tryLock(name, expire);
            if(token == null) {
                if((System.currentTimeMillis()-startTime) > (timeout-50)) {
                    break;
                }
                try {
                    //try 50 per sec
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }while(token==null);

        return token;
    }

    /**
     * 加锁，无阻塞
     * @param name
     * @param expire
     * @return
     */
    public String tryLock(String name, long expire) {
        String token = UUID.randomUUID().toString();
        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        RedisConnection conn = factory.getConnection();
        try{
            Boolean result = conn.set(name.getBytes(Charset.forName("UTF-8")), token.getBytes(Charset.forName("UTF-8")),
                    Expiration.from(expire, TimeUnit.MILLISECONDS), RedisStringCommands.SetOption.SET_IF_ABSENT);
            if(result!=null && result) {
                return token;
            }
        }finally {
            RedisConnectionUtils.releaseConnection(conn, factory);
        }
        return null;
    }

    /**
     * 解锁
     * @param name
     * @param token
     * @return
     */
    public boolean unlock(String name, String token) {
        byte[][] keysAndArgs = new byte[2][];
        keysAndArgs[0] = name.getBytes(Charset.forName("UTF-8"));
        keysAndArgs[1] = token.getBytes(Charset.forName("UTF-8"));
        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        RedisConnection conn = factory.getConnection();
        try {
            Long result = conn.scriptingCommands().eval(unlockScript.getBytes(Charset.forName("UTF-8")), ReturnType.INTEGER, 1, keysAndArgs);
            if(result!=null && result>0) {
                return true;
            }
        }finally {
            RedisConnectionUtils.releaseConnection(conn, factory);
        }

        return false;
    }
}

