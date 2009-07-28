/**********************************************************************
Copyright (c) 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package org.datanucleus.store.appengine;

import org.datanucleus.plugin.Bundle;
import org.datanucleus.plugin.ConfigurationElement;
import org.datanucleus.plugin.Extension;
import org.datanucleus.plugin.ExtensionPoint;
import org.datanucleus.plugin.PluginRegistry;
import org.datanucleus.store.appengine.jpa.DatastoreJPACallbackHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Custom {@link PluginRegistry} that delegates to another
 * {@link PluginRegistry} provided at time of construction for all operations.
 * However, for {@link #getExtensionPoint(String)}, we intercept requests
 * for the callback handler and substitute our own.  This allows us to
 * effectively override the callback handler that is configured in the
 * DataNucleus core plugin.xml (just providing our own value in our own
 * plugin.xml is insufficient because DataNuc the one that gets loaded second
 * is the one that will be used and there are no guarantees about loading
 * order).
 *
 * @author Max Ross <maxr@google.com>
 */
final class DatastorePluginRegistry implements PluginRegistry {

  private final PluginRegistry delegate;

  DatastorePluginRegistry(PluginRegistry delegate) {
    this.delegate = delegate;
  }

  public ExtensionPoint getExtensionPoint(String id) {
    ExtensionPoint ep = delegate.getExtensionPoint(id);
    if (id.equals("org.datanucleus.callbackhandler")) {
      boolean replaced = false;
      for (Extension ext : ep.getExtensions()) {
        for (ConfigurationElement cfg : ext.getConfigurationElements()) {
          if (cfg.getAttribute("name").equals("JPA")) {
            // override with our own callback handler
            // See DatastoreJPACallbackHandler for the reason why we do this.
            cfg.putAttribute("class-name", DatastoreJPACallbackHandler.class.getName());
            replaced = true;
          }
        }
      }

      if (!replaced) {
        throw new RuntimeException("Unable to replace JPACallbackHandler.");
      }
    }
    return ep;
  }

  public ExtensionPoint[] getExtensionPoints() {
    return delegate.getExtensionPoints();
  }

  public void registerExtensionPoints() {
    delegate.registerExtensionPoints();
  }

  public void registerExtensions() {
    delegate.registerExtensions();
  }

  public Object createExecutableExtension(ConfigurationElement confElm, String name,
                                          Class[] argsClass, Object[] args)
      throws ClassNotFoundException, SecurityException, NoSuchMethodException,
             IllegalArgumentException, InstantiationException, IllegalAccessException,
             InvocationTargetException {
    return delegate.createExecutableExtension(confElm, name, argsClass, args);
  }

  public Class loadClass(String pluginId, String className) throws ClassNotFoundException {
    return delegate.loadClass(pluginId, className);
  }

  public URL resolveURLAsFileURL(URL url) throws IOException {
    return delegate.resolveURLAsFileURL(url);
  }

  public void resolveConstraints() {
    delegate.resolveConstraints();
  }

  public Bundle[] getBundles() {
    return delegate.getBundles();
  }
}