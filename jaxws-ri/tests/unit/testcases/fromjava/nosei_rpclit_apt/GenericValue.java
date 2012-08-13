/*
 * Copyright 2004 Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package fromjava.nosei_rpclit_apt;


public final class GenericValue<T> {

  /**
   * The value contained in the holder.
   **/
  public T value;
    
  /**
   * Creates a new holder with a <code>null</code> value.
   **/
  public GenericValue() {
  }

  /**
   * Create a new holder with the specified value.
   *
   * @param value The value to be stored in the holder.
   **/
  public GenericValue(T value) {
      this.value = value;
  }
}
