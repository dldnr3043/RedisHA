# RedisHA
Configure Standalone Redis HA with Pacemaker

# 목차
  1. Redis AOF
  2. dir mount
  3. Pacemaker HA
  4. Failover 감지

# 0. 환경
  - Spring boot project
  - Redis Pub/Sub


# 1. Redis AOF
  - Redis 쓰기 명령어를 로그로 기록하는 AOF 방식 사용
  - appendsync를 always로 하여 쓰기명령어 항상 기록

# 2. dir mount
  - sshfs, nfs, ... 등으로 HA구성할 서버에서 aof 로그파일이 있는 dir mount하여 공유
    - redis data 서버 간 공유

# 3. Pacemaker HA
  - Pacemaker로 Redis server HA 구성
  - Active - Standby

# 4. Failover 감지 in Spring boot project
  - Failover 발생 시 spring에서 connection을 맺고 있는 redis client에게 failover 발생했다는 이벤트 전달할 수 없어 scheduler를 통해 pooling방식 사용
  - Redis AOF는 pubsub 관련 명령은 기록하지 않기 떄문에 subscribe한 channel 기록은 사라짐
  - redis 명령어인 client list를 통해 각 redis client(spring server) ip가 subscribe한 redis client가 없다면 해당 채널 다시 subscribe
