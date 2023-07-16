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
public class AspectClassForLockInArgClass {
  @Autowired private RedissonClient redissonClient;

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisLockAspect.class);

  private ExpressionParser expressionParser = new SpelExpressionParser();
  private ParserContext parserContext = new TemplateParserContext();

  @Before("@annotation(in.dreamplug.charles.spring.annotations.RedisLock)")
  public Object acquireRedisLock(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = getTargetMethod(joinPoint);
    RedisLock annotation = method.getAnnotation(RedisLock.class);
    String key = annotation.key();
    Object[] args = joinPoint.getArgs();
    String getLockKeys = getLockId(annotation.lockId(), args);
    String lockName = key.concat("_").concat(getLockKeys);
    long waitTime = annotation.waitTime();
    long LLTime = annotation.LLTime();
    try {
      RLock lock = redissonClient.getLock(lockName);
      lock.tryLock( waitTime, LLTime, TimeUnit.MILLISECONDS);
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

  private String getLockId(String authExpression, Object[] args)
      throws IllegalAccessException, NoSuchFieldException {
    String[] parseString = expressionParsingHelper(authExpression);
    Expression expression = expressionParser.parseExpression(parseString[0], parserContext);
    Object value = expression.getValue(new RootObject(args), Object.class);
    Class<?> objectType = value.getClass();
    Field temp = value.getClass().getDeclaredField(parseString[1]);
    temp.setAccessible(true);
    System.out.println(objectType.getName());
    System.out.println(objectType.getSimpleName());
    return (String) temp.get(value);
  }

  private String[] expressionParsingHelper(String expression) {
    String[] split = expression.split("\\.");
    if (split.length == 1) {
      return new String[] {expression, ""};
    } else {
      return new String[] {split[0] + "}", split[1].split("}")[0]};
    }
  }

  private static class TemplateparserContext implements ParserContext {
    @Override
    public boolean isTemplate() {
      return true;
    }

    @Override
    public String getExpressionPrefix() {
      return "#{";
    }

    @Override
    public String getExpressionSuffix() {
      return "}";
    }
  }

  protected static class RootObject {
    private final Object[] args;

    private RootObject(Object[] args) {
      super();
      this.args = args;
    }  

    public Object[] getArgs() {
      return args;
    }
  }
    
}
