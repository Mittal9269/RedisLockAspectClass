import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RedisLock{
    String lockId() default "lockId";
    String key() default "key";
    long waitTime() default 0l;
    long LLTime() default 50l;
}