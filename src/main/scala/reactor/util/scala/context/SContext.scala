package reactor.util.scala.context

import reactor.util.context.Context

import scala.reflect.ClassTag

object SContext {
  implicit class ContextOps(context: Context) {

    /**
      * Resolve a value given a key within the [[Context]].
      * <p/>
      * <b>Important:</b> Unlike the basic Context methods,
      * this will actually check the type of the value against the given type parameter,
      * and return [[None]] if the value is not of a compatible type.
      * 
      * @param key a lookup key to resolve the value within the context
      * @return [[Some]] value for that key,
      *         or [[None]] if there is no such key (or if its value is the wrong type).
      */
    def getOrNone[T](key: Any)(implicit valueType: ClassTag[T]): Option[T] =
      context.getOrDefault[Any](key, None) match {
        case value:T => Some(value)
        case _ => None
      }
  }
}