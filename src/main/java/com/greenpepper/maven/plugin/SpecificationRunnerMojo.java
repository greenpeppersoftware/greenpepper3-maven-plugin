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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import com.greenpepper.Statistics;
import com.greenpepper.document.GreenPepperInterpreterSelector;
import com.greenpepper.runner.CompositeSpecificationRunnerMonitor;
import com.greenpepper.runner.RecorderMonitor;
import com.greenpepper.util.IOUtil;
import com.greenpepper.util.StringUtil;

/**
 * @goal run
 * @phase integration-test
 * @requiresDependencyResolution test
 * @description Runs GreenPepper specifications
 */
public class SpecificationRunnerMojo extends AbstractMojo
{
	/**
	 * Set this to 'true' to bypass greenpepper tests entirely.
	 * Its use is NOT RECOMMENDED, but quite convenient on occasion.
	 *
	 * @parameter expression="${maven.greenpepper.test.skip}" default-value="false"
	 */
	private boolean skip;

	/**
     * Project fixture classpath.
     *
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    List classpathElements;

    /**
     * The directory where compiled fixture classes go.
     *
     * @parameter expression="${project.build.directory}/fixture-test-classes"
     * @required
     */                                                        
    File fixtureOutputDirectory;

    /**
     * The SystemUnderDevelopment class to use
     *
     * @parameter default-value="com.greenpepper.systemunderdevelopment.DefaultSystemUnderDevelopment"
     * @required
     */
    String systemUnderDevelopment;
    
    /**
     * @parameter expression="${project.build.directory}/greenpepper-reports"
     * @required
     */
    File reportsDirectory;

	/**
	 * @parameter expression="${maven.greenpepper.reports.type}" default-value="html"
	 */
	String reportsType;

    /**
     * @parameter expression="${greenpepper.repositories}"
     * @required
     */
    ArrayList<Repository> repositories;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @required
     * @readonly
     */
    List<Artifact> pluginDependencies;

	/**
	 * Set this to 'true' to stop the execution on a failure.
	 *
	 * @parameter expression="${maven.greenpepper.test.stop}" default-value="false"
	 */
	boolean stopOnFirstFailure;

	/**
	 * Set the locale for the execution.
	 *
	 * @parameter expression="${maven.greenpepper.locale}"
	 */
	String locale;

	/**
	 * Set the Selector class.
	 *
	 * @parameter expression="${maven.greenpepper.selector}" default-value="com.greenpepper.document.GreenPepperInterpreterSelector"
	 */
	String selector;

	/**
	 * Set the Debug mode.
	 *
	 * @parameter expression="${maven.greenpepper.debug}" default-value="false"
	 */
	boolean debug;

	/**
	 * Set this to true to ignore a failure during testing.
	 * Its use is NOT RECOMMENDED, but quite convenient on occasion.
	 *
	 * @parameter expression="${maven.greenpepper.test.failure.ignore}" default-value="false"
	 */
	boolean testFailureIgnore;

    Statistics statistics;

    boolean testFailed;
    boolean exceptionOccured;

	public SpecificationRunnerMojo()
    {
        this.statistics = new Statistics( );
        this.repositories = new ArrayList<Repository>();
    }

    public void addRepository(Repository repository)
    {
        repositories.add( repository );
    }

	public void execute() throws MojoExecutionException, MojoFailureException
    {
		if ( skip )
		{
			getLog().info( "Not executing specifications." );
		}
		else
		{
			prepareReportsDir();
			printBanner();
			runAllTests();
			printFooter();
			checkTestsResults();
		}
	}

    private void checkTestsResults() throws MojoExecutionException, MojoFailureException
    {
        if (exceptionOccured)
			notifyExceptionsOccured();
        if (testFailed)
			notifyTestsFailed();
    }

	private void notifyExceptionsOccured()
			throws MojoExecutionException
	{
		if (testFailureIgnore)
		{
			getLog().error("Some greenpepper tests did not run\n");
		}
		else
		{
			throw new MojoExecutionException( "Some greenpepper tests did not run" );
		}
	}

	private void notifyTestsFailed()
			throws MojoFailureException
	{
		if (testFailureIgnore)
		{
			getLog().error("There were greenpepper tests failures\n");
		}
		else
		{
			throw new MojoFailureException( "There were greenpepper tests failures" );
		}
	}

    private void printBanner()
    {
        System.out.println();
        System.out.println( "-----------------------------------------------------" );
        System.out.println( " G R E E N  P E P P E R  S P E C I F I C A T I O N S " );
        System.out.println( "-----------------------------------------------------" );
        System.out.println();
    }

    private void runAllTests() throws MojoExecutionException, MojoFailureException
    {
        for (Repository repository : repositories)
        {
			if (shouldStop())
			{
				break;
			}
			
            runAllIn(repository);
        }
    }

    private void runAllIn(Repository repository) throws MojoExecutionException, MojoFailureException
    {
        runTestsIn( repository );
        runSuitesIn( repository );
    }

    private void runSuitesIn(Repository repository)
        throws MojoExecutionException, MojoFailureException
    {
        for (String suite : repository.getSuites())
        {
			if (shouldStop())
			{
				break;
			}

        	String repoCmdOption = repository.getType() + (repository.getRoot() != null ? ";" + repository.getRoot() : "");
            String outputDir = new File(reportsDirectory, repository.getName()).getAbsolutePath();

			List<String> args = args("-f", systemUnderDevelopment, "-s", "-r", repoCmdOption, "-o", outputDir, suite);

			run(args);
        }
    }

    private void runTestsIn(Repository repository)
        throws MojoExecutionException, MojoFailureException
    {
        for (String test : repository.getTests())
        {
			if (shouldStop())
			{
				break;
			}

        	String repoCmdOption = repository.getType() + (repository.getRoot() != null ? ";" + repository.getRoot() : "");
            String outputDir = new File(reportsDirectory, repository.getName()).getAbsolutePath();

			List<String> args = args("-f", systemUnderDevelopment, "-r", repoCmdOption, "-o", outputDir, test);

			run(args);
        }
    }

    private void run(List<String> args) throws MojoExecutionException, MojoFailureException
    {
        DynamicCoreInvoker runner = new DynamicCoreInvoker( createClassLoader() );
        CompositeSpecificationRunnerMonitor monitors = new CompositeSpecificationRunnerMonitor( );
        monitors.add( new LoggerMonitor( getLog() ) );
        RecorderMonitor recorder = new RecorderMonitor();
        monitors.add( recorder );
        runner.setMonitor( monitors );

        try
        {
            runner.run( toArray( args ) );
        }
        catch (Exception e)
        {
            exceptionOccured = true;
            throw new MojoExecutionException( "Unable to run tests", e);
        }

        exceptionOccured |= recorder.hasException();
        testFailed |= recorder.hasTestFailures();
        statistics.tally( recorder.getStatistics() );
    }

    private void printFooter()
    {
        System.out.println();
        System.out.println( "Results:");
        System.out.println( statistics );
        System.out.println();
    }

    @SuppressWarnings("unchecked")
    private ClassLoader createClassLoader() throws MojoExecutionException
    {
        List urls = new ArrayList();
        for (Iterator it = classpathElements.iterator(); it.hasNext();)
        {
            String s = (String) it.next();
            urls.add( toURL( new File( s ) ) );
        }

        urls.add( toURL( fixtureOutputDirectory ) );

		if (!containsGreenPepperCore( urls ))
		{
			urls.add( getDependencyURL( "greenpepper-core" ) );
		}

        urls.add( getDependencyURL( "greenpepper-extensions-java" ) );
        urls.add( getDependencyURL( "xmlrpc" ) );
        urls.add( getDependencyURL( "commons-codec" ) );

        URL[] classpath = (URL[]) urls.toArray( new URL[urls.size()] );

        return new URLClassLoader( classpath, ClassLoader.getSystemClassLoader() );
    }

    private URL getDependencyURL(String name) throws MojoExecutionException
    {
        if (pluginDependencies != null && !pluginDependencies.isEmpty())
        {
            for (Iterator it = pluginDependencies.iterator(); it.hasNext();)
            {
                Artifact artifact = (Artifact) it.next();
                if (artifact.getArtifactId().equals( name ) && artifact.getType().equals( "jar" ))
                    return toURL( artifact.getFile() );
            }
        }
        throw new MojoExecutionException( "Dependency not found: " + name );
    }

    private URL toURL(File f) throws MojoExecutionException
    {
        try
        {
            return f.toURL();
        }
        catch (MalformedURLException e)
        {
            throw new MojoExecutionException( "Invalid dependency: " + f.getAbsolutePath(), e );
        }
    }

	private boolean containsGreenPepperCore(List urls) {

		for (Iterator it = urls.iterator(); it.hasNext();) 
		{
			URL url = (URL)it.next();

			if (url.getFile().indexOf("greenpepper-core") != -1 && url.getFile().endsWith(".jar"))
			{
				return true;
			}
		}

		return false;
	}

    private void prepareReportsDir() throws MojoExecutionException
    {
        try
        {
            IOUtil.createDirectoryTree( reportsDirectory );
        }
        catch (IOException e)
        {
            throw new MojoExecutionException( "Could not create reports directory: " + reportsDirectory.getAbsolutePath() );
        }
    }

	private boolean shouldStop()
	{
		return stopOnFirstFailure && statistics.indicatesFailure();
	}

	private List<String> args(String... args)
	{
		List<String> arguments = new ArrayList<String>();
		arguments.addAll(Arrays.asList(args));

		if (!StringUtil.isEmpty(locale))
		{
			arguments.add("--locale");
			arguments.add(locale);
		}

		if (!StringUtil.isEmpty(selector) && !GreenPepperInterpreterSelector.class.getName().equals(selector))
		{
			arguments.add("--selector");
			arguments.add(selector);
		}

		if ("xml".equalsIgnoreCase(reportsType))
		{
			arguments.add("--xml");
		}

		if (stopOnFirstFailure)
		{
			arguments.add("--stop");
		}

		if (debug)
		{
			arguments.add("--debug");
		}

		return arguments;
	}

	private String[] toArray(List<String> args)
	{
		String[] arguments = new String[args.size()];
		args.toArray(arguments);
		return arguments;
	}
}
