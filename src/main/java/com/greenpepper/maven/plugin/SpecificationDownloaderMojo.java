package com.greenpepper.maven.plugin;

import static java.lang.String.format;

import com.greenpepper.util.IOUtil;
import com.greenpepper.report.FileReportGenerator;
import com.greenpepper.report.Report;
import com.greenpepper.document.Document;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @goal freeze
 * @requiresDependencyResolution test
 * @description Downloads GreenPepper specifications
 */
public class SpecificationDownloaderMojo extends AbstractMojo {
    /**
     * @parameter expression="${basedir}/src/specs"
     * @required
     */
    File specsDirectory;

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

    int specCount;

    public SpecificationDownloaderMojo() {
        this.repositories = new ArrayList<Repository>();
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareSpecsDir();
        printBanner();
        downloadAllSpecs();
        printFooter();
    }

    private void downloadAllSpecs() throws MojoExecutionException, MojoFailureException {
        for (Repository repository : repositories) {
            downloadAllFrom(repository);
        }
    }

    private void downloadAllFrom(Repository repository) throws MojoExecutionException, MojoFailureException {
        downloadTestsFrom(repository);
        downloadSuitesFrom(repository);
    }

    private void downloadSuitesFrom(Repository repository) throws MojoExecutionException {
        for (String suite : repository.getSuites()) {
            List<String> specifications = listDocuments(repository, suite);
            downloadAllSpecifications(repository, specifications);
        }
    }

    private void downloadAllSpecifications(Repository repository, List<String> specifications) throws MojoExecutionException {
        for (String specification : specifications) {
            downloadSpecification(repository, specification);
        }
    }

    private List<String> listDocuments(Repository repository, String suite) throws MojoExecutionException {
        try {
            return repository.getDocumentRepository().listDocuments(suite);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    format("Error retrieving list of specifications %s from %s", suite, repository.getName()));
        }
    }

    private void downloadTestsFrom(Repository repository) throws MojoExecutionException {
        for (String test : repository.getTests()) {
            downloadSpecification(repository, test);
        }
    }

    private void downloadSpecification(Repository repository, String spec) throws MojoExecutionException {
        try {
            FileReportGenerator generator = new FileReportGenerator(new File(specsDirectory, repository.getName()));
            generator.adjustReportFilesExtensions(true);
            Document doc = repository.getDocumentRepository().loadDocument(spec);
            Report report = generator.openReport(spec);
            report.generate(doc);
            generator.closeReport(report);
            countDownload(repository, spec);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    format("Error downloading specification document %s from %s", spec, repository.getName()), e);
        }
    }

    private void countDownload(Repository repository, String spec) {
        getLog().info(format("Downloaded %s from %s", spec, repository.getName()));
        specCount++;
    }

    private void printBanner() {
        System.out.println();
        System.out.println("-----------------------------------------------");
        System.out.println(" F R E E Z I N G   S P E C I F I C A T I O N S ");
        System.out.println("-----------------------------------------------");
        System.out.println();

        getLog().info("Downloading specification files to " + specsDirectory);
    }

    private void printFooter() {
        System.out.println();
        System.out.println(format("Total: downloaded %d specification(s)", specCount));
        System.out.println();
    }

    private void prepareSpecsDir() throws MojoExecutionException {
        try {
            IOUtil.createDirectoryTree(specsDirectory);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Could not create specification directory: " + specsDirectory.getAbsolutePath());
        }
    }
}
