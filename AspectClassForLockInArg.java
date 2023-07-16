import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@org.aspectj.lang.annotation.Aspect
@Component
public class AspectClassForLockInArg {
    @Autowired private RedissonClient redissonClient;

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisLockAspect.class);

  @Before("@annotation(RedisLock)")
  public Object acquireRedisLock(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = getTargetMethod(joinPoint);
    RedisLock annotation = method.getAnnotation(RedisLock.class);
    Object[] args = joinPoint.getArgs();
    String key = annotation.key();
    String lockId = (String) args[args.length-1];
    String lockName = key.concat("_").concat(lockId);
    long waitTime = annotation.waitTime();
    long LLTime = annotation.LLTime();
    try {
        RLock lock = redissonClient.getLock(lockName);
        lock.tryLock( waitTime, LLTime , TimeUnit.MILLISECONDS);
        return joinPoint.proceed();
    } catch (InterruptedException e) {
        LOGGER.error("Exception while trying to acquire the redis lock", e);
    } catch (Exception e) {
        LOGGER.error("Unhandled Exception while trying to acquire the redis lock", e);
    } 
    return null;
  }


  @After("@annotation(RedisLock)")
  public void releaseLock(ProceedingJoinPoint joinPoint){
    Method method = getTargetMethod(joinPoint);
    RedisLock annotation = method.getAnnotation(RedisLock.class);
   Object[] args = joinPoint.getArgs();
    String key = annotation.key();
    String lockId = (String) args[args.length-1];
    String lockName = key.concat("_").concat(lockId);
    RLock lock = redissonClient.getLock(lockName);
    if(lock.isLocked() && lock.isHoldByCurrentThread()){
        lock.unlock();
    }
  }

  private Method getTargetMethod(ProceedingJoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    return methodSignature.getMethod();
  }
    
}
