package com.greenpepper.maven.plugin;

import static com.greenpepper.util.CollectionUtil.toVector;
import com.greenpepper.util.URIUtil;
import com.greenpepper.util.IOUtil;
import com.greenpepper.repository.FileSystemRepository;
import com.greenpepper.runner.repository.AtlassianRepository;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.xmlrpc.WebServer;
import org.jmock.Mock;
import org.jmock.core.matcher.InvokeOnceMatcher;
import org.jmock.core.stub.ReturnStub;
import org.jmock.core.constraint.IsEqual;
import org.jmock.core.Constraint;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;

public class SpecificationDownloaderMojoTest extends AbstractMojoTestCase {

    private SpecificationDownloaderMojo mojo;
    private WebServer ws;
    private Mock handler;

    protected void tearDown() throws Exception {
        stopWebServer();
    }

    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        super.setUp();
        URL pomPath = SpecificationDownloaderMojoTest.class.getResource("pom-downloader.xml");
        mojo = (SpecificationDownloaderMojo) lookupMojo("freeze", URIUtil.decoded(pomPath.getPath()));

        mojo.pluginDependencies = new ArrayList<Artifact>();
        mojo.pluginDependencies.add(new DependencyArtifact("commons-codec", dependency("commons-codec-1.3.jar")));
        mojo.pluginDependencies.add(new DependencyArtifact("xmlrpc", dependency("xmlrpc-2.0.1.jar")));
        File extension = dependency("greenpepper-extensions-java.jar");
        mojo.pluginDependencies.add(new DependencyArtifact("greenpepper-extensions-java", extension));
    }

    private Repository createLocalRepository(String name) throws URISyntaxException {
        Repository repository = new Repository();
        repository.setName(name);
        repository.setType( FileSystemRepository.class.getName() );
        repository.setRoot(localPath());
        mojo.addRepository(repository);
        return repository;
    }

    private String localPath() throws URISyntaxException {
        return localDir().getAbsolutePath();
    }

    private File localDir() throws URISyntaxException {
        return spec("spec.html").getParentFile();
    }

    private File dependency(String name) throws URISyntaxException {
        return new File(classesOutputDir(), name);
    }

    private File classesOutputDir()
            throws URISyntaxException {
        return localDir().getParentFile().getParentFile().getParentFile().getParentFile();
    }

    public void testCanDownloadASingleSpecification() throws Exception
    {
        createLocalRepository("repo").addTest( "spec.html" );
        mojo.execute();

        assertDownloaded("repo", "spec.html");
    }

    public void testCanDownloadASuiteOfSpecifications() throws Exception
    {
        createLocalRepository("repo").addSuite( "/" );
        try
        {
            mojo.execute();
        }
        catch (MojoFailureException ignored)
        {
        }
        assertDownloaded("repo", "spec.html");
        assertDownloaded("repo", "right.html");
        assertDownloaded("repo", "wrong.html");
        assertDownloaded("repo", "guice.html");
    }

    public void testSupportsMultipleRepositories() throws Exception
    {
        createLocalRepository( "first" ).addTest( "right.html" );
        createLocalRepository( "second" ).addTest( "wrong.html" );
        try
        {
            mojo.execute();
        }
        catch (MojoFailureException ignored)
        {
        }
        assertDownloaded("first", "right.html");
        assertDownloaded("second", "wrong.html");
    }

	public void testShouldSupportCustomRepositoriesSuchAsAtlassian() throws Exception
    {
        startWebServer();
    	Vector<?> expected = toVector( "SPACE", "PAGE", Boolean.TRUE, Boolean.TRUE  );
        String right = IOUtil.readContent( spec( "spec.html" ) );
        handler.expects( new InvokeOnceMatcher( ) ).method( "getRenderedSpecification" ).with( eq( "" ), eq( "" ), eq( expected ) ).will( new ReturnStub( right ) );

        createAtlassianRepository("repo").addTest("PAGE");
        mojo.execute();

        handler.verify();
        assertDownloaded("repo", "PAGE.html");
    }

    private Repository createAtlassianRepository(String name) {
        Repository repository = new Repository();
        repository.setName(name);
        repository.setType( AtlassianRepository.class.getName() );
        repository.setRoot("http://localhost:19005/rpc/xmlrpc?includeStyle=true&handler=greenpepper1#SPACE");
        mojo.addRepository(repository);
        return repository;
    }

    private Constraint eq(Object o)
    {
    	return new IsEqual(o);
    }

    public void testShouldMakeBuildFailIfSomeSpecsCouldNotBeDownloaded() throws Exception
    {
        Repository repository = createLocalRepository( "repo" );
        repository.addTest( "no_such_file.html" );
        mojo.addRepository(repository);
        try
        {
            mojo.execute();
            fail();
        }
        catch (MojoExecutionException expected)
        {
            assertTrue( true );
        }
    }

    private File output(String repo, String name)
            throws URISyntaxException {
        return new File( new File(mojo.specsDirectory, repo), URIUtil.flatten(name) );
    }

    private File spec(String name) throws URISyntaxException {
        return new File(URIUtil.decoded(SpecificationRunnerMojoTest.class.getResource(name).getPath()));
    }

    private void assertDownloaded(String repo, String name) throws URISyntaxException {
        File out = output(repo, name);
        assertTrue(out.exists());
        long length = out.length();
        out.delete();
        assertTrue(length > 0);
    }

    private void startWebServer() {
        ws = new WebServer(19005);
        handler = new Mock(Handler.class);
        ws.addHandler("greenpepper1", handler.proxy());
        ws.start();
    }

    private void stopWebServer() {
        if (ws != null) ws.shutdown();
    }

    public static interface Handler {

        String getRenderedSpecification(String username, String password, Vector<Object> args);
    }
}
