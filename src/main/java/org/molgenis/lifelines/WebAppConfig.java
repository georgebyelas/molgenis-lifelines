package org.molgenis.lifelines;

import javax.xml.validation.Schema;

import nl.umcg.hl7.CatalogService;
import nl.umcg.hl7.GenericLayerCatalogService;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.molgenis.DatabaseConfig;
import org.molgenis.catalogmanager.CatalogManagerService;
import org.molgenis.elasticsearch.config.EmbeddedElasticSearchConfig;
import org.molgenis.framework.db.Database;
import org.molgenis.lifelines.catalog.GenericLayerCatalogueManagerService;
import org.molgenis.lifelines.catalog.LifeLinesCatalogManagerService;
import org.molgenis.lifelines.resourcemanager.GenericLayerResourceManagerService;
import org.molgenis.lifelines.studymanager.GenericLayerDataQueryService;
import org.molgenis.lifelines.studymanager.GenericLayerStudyManagerService;
import org.molgenis.lifelines.studymanager.LifeLinesStudyManagerService;
import org.molgenis.lifelines.utils.GenericLayerDataBinder;
import org.molgenis.omx.OmxConfig;
import org.molgenis.omx.catalogmanager.OmxCatalogManagerService;
import org.molgenis.omx.config.DataExplorerConfig;
import org.molgenis.omx.studymanager.OmxStudyManagerService;
import org.molgenis.search.SearchSecurityConfig;
import org.molgenis.studymanager.StudyManagerService;
import org.molgenis.ui.MolgenisWebAppConfig;
import org.molgenis.util.SchemaLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@EnableAsync
@ComponentScan("org.molgenis")
@Import(
{ WebAppSecurityConfig.class, DatabaseConfig.class, OmxConfig.class, EmbeddedElasticSearchConfig.class,
		DataExplorerConfig.class, SearchSecurityConfig.class })
public class WebAppConfig extends MolgenisWebAppConfig
{
	@Autowired
	private Database database;

	@Bean
	public HttpClient httpClient()
	{
		DefaultHttpClient defaultHttpClient = new DefaultHttpClient(connectionManager());
		HttpParams httpParams = defaultHttpClient.getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, 2000);
		HttpConnectionParams.setSoTimeout(httpParams, 30000);
		return new DecompressingHttpClient(defaultHttpClient);
	}

	@Bean(destroyMethod = "shutdown")
	protected ClientConnectionManager connectionManager()
	{
		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
		connectionManager.setDefaultMaxPerRoute(10);
		connectionManager.setMaxTotal(20);
		return connectionManager;
	}

	@Value("${lifelines.resource.manager.service.url}")
	private String resourceManagerServiceUrl; // Specify in molgenis-server.properties
	@Autowired
	private GenericLayerDataQueryService genericLayerDataQueryService;

	@Bean
	public GenericLayerDataBinder genericLayerDataBinder()
	{
		Schema eMeasureSchema = new SchemaLoader("EMeasure.xsd").getSchema();
		return new GenericLayerDataBinder(eMeasureSchema);
	}

	@Value("${lifelines.profile:@null}")
	private String appProfile;

	@Bean
	public CatalogManagerService catalogManagerService()
	{
		if (appProfile == null || LifeLinesAppProfile.valueOf(appProfile.toUpperCase()) == LifeLinesAppProfile.WEBSITE)
		{
			return new LifeLinesCatalogManagerService(new OmxCatalogManagerService(database));
		}
		else
		{
			GenericLayerCatalogService genericLayerCatalogService = new CatalogService()
					.getBasicHttpBindingGenericLayerCatalogService();
			return new GenericLayerCatalogueManagerService(database, genericLayerCatalogService,
					genericLayerResourceManagerService());
		}
	}

	@Bean
	public StudyManagerService studyDefinitionManagerService()
	{
		GenericLayerStudyManagerService genericLayerStudyManagerService = new GenericLayerStudyManagerService(
				genericLayerResourceManagerService(), catalogManagerService(), genericLayerDataQueryService);
		if (appProfile == null || LifeLinesAppProfile.valueOf(appProfile.toUpperCase()) == LifeLinesAppProfile.WEBSITE)
		{
			return new LifeLinesStudyManagerService(genericLayerStudyManagerService, new OmxStudyManagerService(
					database), LifeLinesAppProfile.valueOf(appProfile.toUpperCase()));
		}
		else
		{
			return genericLayerStudyManagerService;
		}
	}

	@Bean
	public GenericLayerResourceManagerService genericLayerResourceManagerService()
	{
		return new GenericLayerResourceManagerService(httpClient(), resourceManagerServiceUrl, genericLayerDataBinder());
	}
}