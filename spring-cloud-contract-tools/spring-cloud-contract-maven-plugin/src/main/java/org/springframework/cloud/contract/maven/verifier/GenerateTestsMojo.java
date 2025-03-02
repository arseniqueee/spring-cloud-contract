/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.contract.maven.verifier;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;

import org.springframework.cloud.contract.spec.ContractVerifierException;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.contract.verifier.TestGenerator;
import org.springframework.cloud.contract.verifier.config.ContractVerifierConfigProperties;
import org.springframework.cloud.contract.verifier.config.TestFramework;
import org.springframework.cloud.contract.verifier.config.TestMode;

/**
 * From the provided directory with contracts generates the acceptance tests on the
 * producer side.
 *
 * @author Mariusz Smykula
 */
@Mojo(name = "generateTests", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
		requiresDependencyResolution = ResolutionScope.TEST)
public class GenerateTestsMojo extends AbstractMojo {

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;

	@Parameter(property = "spring.cloud.contract.verifier.contractsDirectory",
			defaultValue = "${project.basedir}/src/test/resources/contracts")
	private File contractsDirectory;

	@Parameter(defaultValue = "${project.build.directory}/generated-test-sources/contracts")
	private File generatedTestSourcesDir;

	@Parameter(defaultValue = "${project.build.directory}/generated-test-resources/contracts")
	private File generatedTestResourcesDir;

	@Parameter
	private String basePackageForTests;

	@Parameter
	private String baseClassForTests;

	@Parameter(defaultValue = "MOCKMVC")
	private TestMode testMode;

	@Parameter(defaultValue = "JUNIT5")
	private TestFramework testFramework;

	@Parameter
	private String ruleClassForTests;

	@Parameter
	private String nameSuffixForTests;

	/**
	 * Imports that should be added to generated tests.
	 */
	@Parameter
	private String[] imports;

	/**
	 * Static imports that should be added to generated tests.
	 */
	@Parameter
	private String[] staticImports;

	/**
	 * Patterns that should not be taken into account for processing.
	 */
	@Parameter
	private List<String> excludedFiles;

	/**
	 * Patterns that should be taken into account for processing.
	 */
	@Parameter(property = "includedFiles")
	private List<String> includedFiles;

	/**
	 * Incubating feature. You can check the size of JSON arrays. If not turned on
	 * explicitly will be disabled.
	 */
	@Parameter(property = "spring.cloud.contract.verifier.assert.size", defaultValue = "false")
	private boolean assertJsonSize;

	/**
	 * Patterns for which Spring Cloud Contract Verifier should generate @Ignored tests.
	 */
	@Parameter
	private List<String> ignoredFiles;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(property = "spring.cloud.contract.verifier.skip", defaultValue = "false")
	private boolean skip;

	@Parameter(property = "maven.test.skip", defaultValue = "false")
	private boolean mavenTestSkip;

	/**
	 * The URL from which a contracts should get downloaded. If not provided but
	 * artifactid / coordinates notation was provided then the current Maven's build
	 * repositories will be taken into consideration.
	 */
	@Parameter(property = "contractsRepositoryUrl")
	private String contractsRepositoryUrl;

	@Parameter(property = "contractDependency")
	private Dependency contractDependency;

	/**
	 * The path in the JAR with all the contracts where contracts for this particular
	 * service lay. If not provided will be resolved to {@code groupid/artifactid}.
	 * Example: If {@code groupid} is {@code com.example} and {@code artifactid} is
	 * {@code service} then the resolved path will be {@code /com/example/artifactid}
	 */
	@Parameter(property = "contractsPath")
	private String contractsPath;

	/**
	 * Picks the mode in which stubs will be found and registered.
	 */
	@Parameter(property = "contractsMode", defaultValue = "CLASSPATH")
	private StubRunnerProperties.StubsMode contractsMode;

	/**
	 * A package that contains all the base clases for generated tests. If your contract
	 * resides in a location {@code src/test/resources/contracts/com/example/v1/} and you
	 * provide the {@code packageWithBaseClasses} value to
	 * {@code com.example.contracts.base} then we will search for a test source file that
	 * will have the package {@code com.example.contracts.base} and name
	 * {@code ExampleV1Base}. As you can see it will take the two last folders to and
	 * attach {@code Base} to its name.
	 */
	@Parameter(property = "packageWithBaseClasses")
	private String packageWithBaseClasses;

	/**
	 * A way to override any base class mappings. The keys are regular expressions on the
	 * package name of the contract and the values FQN to a base class for that given
	 * expression. Example of a mapping {@code .*.com.example.v1..*} ->
	 * {@code com.example.SomeBaseClass} When a contract's package matches the provided
	 * regular expression then extending class will be the one provided in the map - in
	 * this case {@code com.example.SomeBaseClass}.
	 */
	@Parameter(property = "baseClassMappings")
	private List<BaseClassMapping> baseClassMappings;

	/**
	 * The user name to be used to connect to the repo with contracts.
	 */
	@Parameter(property = "contractsRepositoryUsername")
	private String contractsRepositoryUsername;

	/**
	 * The password to be used to connect to the repo with contracts.
	 */
	@Parameter(property = "contractsRepositoryPassword")
	private String contractsRepositoryPassword;

	/**
	 * The proxy host to be used to connect to the repo with contracts.
	 */
	@Parameter(property = "contractsRepositoryProxyHost")
	private String contractsRepositoryProxyHost;

	/**
	 * The proxy port to be used to connect to the repo with contracts.
	 */
	@Parameter(property = "contractsRepositoryProxyPort")
	private Integer contractsRepositoryProxyPort;

	/**
	 * If set to {@code false} will NOT delete stubs from a temporary folder after running
	 * tests.
	 */
	@Parameter(property = "deleteStubsAfterTest", defaultValue = "true")
	private boolean deleteStubsAfterTest;

	/**
	 * Map of properties that can be passed to custom
	 * {@link org.springframework.cloud.contract.stubrunner.StubDownloaderBuilder}.
	 */
	@Parameter(property = "contractsProperties")
	private Map<String, String> contractsProperties = new HashMap<>();

	/**
	 * When enabled, this flag will tell stub runner to throw an exception when no stubs /
	 * contracts were found.
	 */
	@Parameter(property = "failOnNoContracts", defaultValue = "true")
	private boolean failOnNoContracts;

	/**
	 * If set to true then if any contracts that are in progress are found, will break the
	 * build. On the producer side you need to be explicit about the fact that you have
	 * contracts in progress and take into consideration that you might be causing false
	 * positive test execution results on the consumer side.
	 */
	@Parameter(property = "failOnInProgress", defaultValue = "true")
	private boolean failOnInProgress = true;

	/**
	 * If set to true then tests are created only when contracts have changed since last
	 * build.
	 */
	@Parameter(property = "incrementalContractTests", defaultValue = "true")
	private boolean incrementalContractTests = true;

	@Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
	private MojoExecution mojoExecution;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (this.skip || this.mavenTestSkip) {
			if (this.skip) {
				getLog().info("Skipping Spring Cloud Contract Verifier execution: spring.cloud.contract.verifier.skip="
						+ this.skip);
			}
			if (this.mavenTestSkip) {
				getLog().info(
						"Skipping Spring Cloud Contract Verifier execution: maven.test.skip=" + this.mavenTestSkip);
			}
			return;
		}
		getLog().info("Generating server tests source code for Spring Cloud Contract Verifier contract verification");
		final ContractVerifierConfigProperties config = new ContractVerifierConfigProperties();
		config.setFailOnInProgress(this.failOnInProgress);
		// download contracts, unzip them and pass as output directory
		File contractsDirectory = new MavenContractsDownloader(this.project, this.contractDependency,
				this.contractsPath, this.contractsRepositoryUrl, this.contractsMode, getLog(),
				this.contractsRepositoryUsername, this.contractsRepositoryPassword, this.contractsRepositoryProxyHost,
				this.contractsRepositoryProxyPort, this.deleteStubsAfterTest, this.contractsProperties,
				this.failOnNoContracts).downloadAndUnpackContractsIfRequired(config, this.contractsDirectory);
		getLog().info("Directory with contract is present at [" + contractsDirectory + "]");

		if (this.incrementalContractTests
				&& !ChangeDetector.inputFilesChangeDetected(contractsDirectory, mojoExecution, session)) {
			getLog().info("Nothing to generate - all classes are up to date");
			return;
		}

		setupConfig(config, contractsDirectory);
		this.project.addTestCompileSourceRoot(this.generatedTestSourcesDir.getAbsolutePath());
		Resource resource = new Resource();
		resource.setDirectory(this.generatedTestResourcesDir.getAbsolutePath());
		this.project.addTestResource(resource);
		if (getLog().isInfoEnabled()) {
			getLog().info("Test Source directory: " + this.generatedTestSourcesDir.getAbsolutePath() + " added.");
			getLog().info("Using [" + config.getBaseClassForTests() + "] as base class for test classes, ["
					+ config.getBasePackageForTests() + "] as base " + "package for tests, ["
					+ config.getPackageWithBaseClasses() + "] as package with " + "base classes, base class mappings "
					+ this.baseClassMappings);
		}
		try {
			LeftOverPrevention leftOverPrevention = new LeftOverPrevention(this.generatedTestSourcesDir, mojoExecution,
					session);
			TestGenerator generator = new TestGenerator(config);
			int generatedClasses = generator.generate();
			getLog().info("Generated " + generatedClasses + " test classes.");
			leftOverPrevention.deleteLeftOvers();
		}
		catch (ContractVerifierException e) {
			throw new MojoExecutionException(
					String.format("Spring Cloud Contract Verifier Plugin exception: %s", e.getMessage()), e);
		}
	}

	private void setupConfig(ContractVerifierConfigProperties config, File contractsDirectory) {
		config.setContractsDslDir(contractsDirectory);
		config.setGeneratedTestSourcesDir(this.generatedTestSourcesDir);
		config.setGeneratedTestResourcesDir(this.generatedTestResourcesDir);
		config.setTestFramework(this.testFramework);
		config.setTestMode(this.testMode);
		config.setBasePackageForTests(this.basePackageForTests);
		config.setBaseClassForTests(this.baseClassForTests);
		config.setRuleClassForTests(this.ruleClassForTests);
		config.setNameSuffixForTests(this.nameSuffixForTests);
		config.setImports(this.imports);
		config.setStaticImports(this.staticImports);
		config.setIgnoredFiles(this.ignoredFiles);
		config.setExcludedFiles(this.excludedFiles);
		config.setIncludedFiles(this.includedFiles);
		config.setAssertJsonSize(this.assertJsonSize);
		config.setPackageWithBaseClasses(this.packageWithBaseClasses);
		if (this.baseClassMappings != null) {
			config.setBaseClassMappings(mappingsToMap());
		}
	}

	public Map<String, String> mappingsToMap() {
		Map<String, String> map = new HashMap<>();
		if (this.baseClassMappings == null) {
			return map;
		}
		for (BaseClassMapping mapping : this.baseClassMappings) {
			map.put(mapping.getContractPackageRegex(), mapping.getBaseClassFQN());
		}
		return map;
	}

	public List<String> getExcludedFiles() {
		return this.excludedFiles;
	}

	public void setExcludedFiles(List<String> excludedFiles) {
		this.excludedFiles = excludedFiles;
	}

	public List<String> getIgnoredFiles() {
		return this.ignoredFiles;
	}

	public void setIgnoredFiles(List<String> ignoredFiles) {
		this.ignoredFiles = ignoredFiles;
	}

	public boolean isAssertJsonSize() {
		return this.assertJsonSize;
	}

	public void setAssertJsonSize(boolean assertJsonSize) {
		this.assertJsonSize = assertJsonSize;
	}

}
