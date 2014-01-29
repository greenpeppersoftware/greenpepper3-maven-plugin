/*
 * Copyright (c) 2007 Pyxis Technologies inc.
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA,
 * or see the FSF site: http://www.fsf.org.
 */

package com.greenpepper.maven.plugin;

import com.greenpepper.runner.NullSpecificationRunnerMonitor;
import com.greenpepper.runner.SpecificationRunnerMonitor;

public class DynamicCoreInvoker
{
    private final ClassLoader classLoader;
    private SpecificationRunnerMonitor monitor;

    public DynamicCoreInvoker(ClassLoader classLoader)
    {
        this.classLoader = classLoader;
        monitor = new NullSpecificationRunnerMonitor();
    }

    public void setMonitor(SpecificationRunnerMonitor monitor)
    {
        this.monitor = monitor;
    }

    public void run(String... args) throws Exception
    {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader( classLoader  );

        try
        {
            doRun( args);
        }
        catch (Exception e)
        {
            monitor.exceptionOccured( e );
        }

        Thread.currentThread().setContextClassLoader( old );
    }

    private void doRun(String... args) throws Exception
    {
        Object runner = loadClass( "com.greenpepper.runner.CommandLineRunner" ).newInstance();
        runner.getClass().getMethod( "setMonitor" , Object.class ).invoke( runner, monitor );
        runner.getClass().getMethod( "run", String[].class ).invoke( runner, new Object[] { args  } );
    }

    private Class<?> loadClass(String name) throws ClassNotFoundException
    {
        return classLoader.loadClass( name );
    }
}
