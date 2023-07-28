import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;


/**
 * Redis Standalone n중화 관련 처리 클래스
 *
 * 1. Redis N중화
 * ㄴ redis     : AOF backup 방식 사용, appendsync=always로 항상 기록
 * ㄴ mount     : 각 Redis server dir mount로 aof 파일 공유
 * ㄴ pacemaker : Active-Standby HA 구성
 *
 * ㄴ 주의사항
 *   1. pacemaker에서 failover 발생 시 client(lettuce)가 전달 받을 방법이 없어 @Scheduled로 polling 방법 사용
 *   2. Redis AOF는 pub/sub 쓰기 명령은 기록하지 않아 failover 발생하여 구동될 시 구독하고 있는 채널이 사라지기 때문에  직접 Channel Sub 해줌
 *
 * @see RedisConfig
 * @author 이동욱
 * @date 2023-07-21
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class TalkRedisFailoverCheck {

    private final Env env;
    private final LettuceConnectionFactory redisConnectionFactory;
    private final MessageListenerAdapter listenerAdapter;

    /**
     * 3초마다 실행
     * 1. Failover Check 사용여부가 Y일 때만 수행
     * 2. checkForFailoverRedis()로 redis failover 발생여부 확인하여 실행
     */
    @Scheduled(fixedRate = 3000)
    public void processFailoverRedis() throws Exception {
        String enabledFailoverChk = env.getString("ENABLED_FAILOVER_CHECK");
        if("Y".equals(enabledFailoverChk)) {            // Failover Check 사용여부가 Y일 때만 수행
            if(checkForFailoverRedis(enabledFailoverChk)) {               // Failover가 됐을때만 수행
                log.warn("Redis Connection Reset because of Failover :::::::::::");
                redisConnectionFactory.resetConnection();
                redisConnectionFactory.getConnection().subscribe(listenerAdapter, "teletalkMessageQueue".getBytes());   // 구독
            }
        }
    }

    /**
     * Redis Failover 확인
     * ㄴ 해당 서버가 subscribe한 게 없으면 failover 발생한 것
     * @see RedisConfig
     * @return failover 발생여부
     */
    private boolean checkForFailoverRedis(String enabled) throws UnknownHostException {
        boolean ret = false;
        if("Y".equals(enabled)) {
            List<RedisClientInfo> clientList = redisConnectionFactory.getConnection().getClientList();
            String clientHostName = InetAddress.getLocalHost().getHostName();

            long subCnt = clientList != null ? clientList.stream()
                    .filter(i -> clientHostName.equals(i.get("name")) && "subscribe".equals(i.get("cmd")))
                    .count() : 0;

            if(subCnt == 0) ret = true;
        }

        return ret;
    }
}
