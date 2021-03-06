/*
 * Copyright 2011 JBoss, a divison Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.ioc.rebind.ioc.codegen.builder.impl;

import org.jboss.errai.ioc.rebind.ioc.codegen.BlockStatement;
import org.jboss.errai.ioc.rebind.ioc.codegen.DefModifiers;
import org.jboss.errai.ioc.rebind.ioc.codegen.DefParameters;
import org.jboss.errai.ioc.rebind.ioc.codegen.Modifier;
import org.jboss.errai.ioc.rebind.ioc.codegen.ThrowsDeclaration;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.BlockBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.MethodBlockBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.MethodBuildCallback;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaClass;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaClassFactory;

/**
 * @author Christian Sadilek <csadilek@redhat.com>
 * @author Mike Brock <cbrock@redhat.com>
 */
public class MethodBlockBuilderImpl<T> extends BlockBuilderImpl<T>
        implements MethodBlockBuilder<T> {

  protected ThrowsDeclaration throwsDeclaration = ThrowsDeclaration.none();
  protected MethodBuildCallback<T> callback;
  protected DefParameters defParameters;
  protected DefModifiers modifiers = new DefModifiers();


  public MethodBlockBuilderImpl(MethodBuildCallback<T> callback) {
    this.callback = callback;
  }

  public MethodBlockBuilderImpl(BlockStatement blockStatement, MethodBuildCallback<T> callback) {
    this.blockStatement = blockStatement;
    this.callback = callback;
  }

  @Override
  public BlockBuilder<T> throws_(Class<? extends Throwable>... exceptionTypes) {
    throwsDeclaration = ThrowsDeclaration.of(exceptionTypes);
    return this;
  }

  @Override
  public BlockBuilder<T> throws_(MetaClass... exceptions) {
    throwsDeclaration = ThrowsDeclaration.of(exceptions);
    return this;
  }


  @Override
  public MethodBlockBuilder<T> modifiers(Modifier... modifiers) {
    for (Modifier m : modifiers) {
      switch (m) {
        case Transient:
        case Volatile:
          throw new RuntimeException("illegal modifier for method: " + m);

        default:
          this.modifiers.addModifiers(m);
      }
    }

    return this;
  }

  @Override
  public MethodBlockBuilder<T> parameters(DefParameters parms) {
    defParameters = parms;
    return this;
  }

  @Override
  public MethodBlockBuilder<T> parameters(Class<T>... parms) {
    defParameters = DefParameters.fromTypeArray(MetaClassFactory.fromClassArray(parms)) ;
    return this;
  }

  @Override
  public MethodBlockBuilder<T> parameters(MetaClass... parms) {
    defParameters = DefParameters.fromTypeArray(parms);
    return null;
  }

  @Override
  public BlockBuilder<T> body() {
    return this;
  }

  @Override
  public T finish() {
    if (callback != null) {
      return callback.callback(blockStatement, defParameters, modifiers, throwsDeclaration);
    }
    return null;
  }
}
