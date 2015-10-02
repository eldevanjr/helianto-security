package org.helianto.install.service;

import java.util.List;

import javax.inject.Inject;

import org.helianto.core.domain.City;
import org.helianto.core.domain.Country;
import org.helianto.core.domain.Entity;
import org.helianto.core.domain.Identity;
import org.helianto.core.domain.Operator;
import org.helianto.core.domain.State;
import org.helianto.core.repository.CityRepository;
import org.helianto.core.repository.CountryRepository;
import org.helianto.core.repository.EntityRepository;
import org.helianto.core.repository.IdentityRepository;
import org.helianto.core.repository.OperatorRepository;
import org.helianto.core.repository.StateRepository;
import org.helianto.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Base class to strategies that install important entities when the database is clean.
 * 
 * @author mauriciofernandesdecastro
 */
@PropertySource("classpath:/META-INF/app.properties")
public abstract class AbstractEntityInstallStrategy 
	implements EntityInstallStrategy, InitializingBean
{

	protected static final Logger logger = LoggerFactory.getLogger(AbstractEntityInstallStrategy.class);
	
	protected static final String DEFAULT_CONTEXT_NAME = "DEFAULT";
	
	protected static final String DEFAULT_CONTEXT_DATA_PATH = "/META-INF/data/";
	
	protected static final String DEFAULT_COUNTRY_FILE = "countries.xml";
	
	@Inject
	private OperatorRepository contextRepository;
	
	@Inject
	private CountryRepository countryRepository;
	
	@Inject
	private StateRepository stateRepository;
	
	@Inject
	private CityRepository cityRepository;
	
	@Inject
	private IdentityRepository identityRepository;
	
	@Inject
	private IdentityCrypto identityCrypto; 
	
	@Inject
	private EntityRepository entityRepository;

	@Inject
	private  UserInstallService userInstallService;
	
	@Inject
	private CountryParser countryParser;
	
	@Inject
	private StateParser stateParser;
	
	@Inject
	private CityParser cityParser;
	
    protected String contextDataPath;
    
	@Inject
	private Environment env;
	
	/**
	 * Default country.
	 */
	protected abstract String getDefaultCountry();
	
	/**
	 * States will be read from this file.
	 */
	protected abstract String getDefaultStateFile();
	
	/**
	 * Initial secret. First password is generated by this string.
	 */
	protected abstract String getInitialSecret();
	
	/**
	 * Method to run once after the first installation.
	 * 
	 * @param context
	 * @param rootEntity
	 * @param rootUser
	 */
	protected abstract void runOnce(Operator context, Entity rootEntity, User rootUser);
	
	/**
	 * 
	 * @throws Exception
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		contextDataPath = env.getProperty("helianto.contextDataPath", DEFAULT_CONTEXT_DATA_PATH);
		String contextName = env.getProperty("helianto.defaultContextName", DEFAULT_CONTEXT_NAME);
		String contextDataLocation = env.getProperty("helianto.defaultContextName", DEFAULT_CONTEXT_NAME);

		System.err.println(">>>>>>>>>");
		Operator context = contextRepository.findByOperatorName(contextName);
		if (context==null) {
			context = contextRepository.saveAndFlush(new Operator(contextName));
			logger.info("Created {}.", context);
			Country country = installCountries(context);
			City city = installStatesAndCities(context, country);
			runOnce(context, city);
		}
		System.err.println("<<<<<<<<<");
	}
	
	/**
	 * Run once at installation
	 */
	protected final void runOnce(Operator context, City rootCity) {
		String rootEntityAlias = env.getProperty("helianto.rootEntityAlias", DEFAULT_CONTEXT_NAME);
		String rootPrincipal = env.getRequiredProperty("helianto.rootPrincipal");
		String rootFirstName = env.getRequiredProperty("helianto.rootFirstName");
		String rootLastName = env.getRequiredProperty("helianto.rootLastName");
		String rootDisplayName = env.getProperty("helianto.rootDisplayName", rootFirstName);
		String initialSecret = env.getProperty("helianto.initialSecret", getInitialSecret());
		
		// Root identity
		Identity rootIdentity = identityRepository.findByPrincipal(rootPrincipal);
		if(rootIdentity==null){
			rootIdentity= new Identity(rootPrincipal);
			rootIdentity.setDisplayName(rootDisplayName);
			rootIdentity.getPersonalData().setFirstName(rootFirstName);
			rootIdentity.getPersonalData().setLastName(rootLastName);
			rootIdentity = identityRepository.saveAndFlush(rootIdentity);
			logger.info("Created root identity {}.", rootIdentity);	
			identityCrypto.createIdentitySecret(rootIdentity, initialSecret, false);
		}

		// Root entity
		Entity rootEntity = new Entity(context, rootEntityAlias);
		rootEntity.setCity(rootCity);
		rootEntity = installEntity(context, rootEntity);
		
		// Root user
		User rootUser = userInstallService.installUser(rootEntity, rootIdentity.getPrincipal());
		runOnce(context, rootEntity, rootUser);
	}
	
	/**
	 * Install countries, return the root country.
	 * 
	 * @param context
	 * @param contextDataPath
	 */
	protected Country installCountries(Operator context) {
		String countryFile = env.getProperty("helianto.countryFile", DEFAULT_COUNTRY_FILE);
		String defaultCountry = env.getProperty("helianto.defaultCountry", getDefaultCountry());
		
		// All countries
		Resource countryResource = new ClassPathResource(contextDataPath+countryFile);
		List<Country> countries = countryParser.parseCountries(context, countryResource);
		List<Country> managedCountries = countryRepository.save(countries);
		logger.info("Saved {} countries.", managedCountries.size());
		
		// Our country
		Country country = countryRepository.findByOperatorAndCountryCode(context, defaultCountry);
		return country;
	}
	
	/**
	 * Install states and cities, return the root city.
	 * 
	 * @param context
	 * @param contextDataPath
	 */
	protected City installStatesAndCities(Operator context, Country country) {
		String stateFile = env.getProperty("helianto.stateFile", getDefaultStateFile());
		String rootEntityStateCode = env.getRequiredProperty("helianto.rootEntityStateCode");
		String rootEntityCityCode = env.getRequiredProperty("helianto.rootEntityCityCode");
		
		if (country==null) {
			throw new IllegalArgumentException("Please, provide required country to allow for default city resolution.");
		}
		
		// States
		Resource stateResource = new ClassPathResource(contextDataPath+stateFile);
		List<State> states = stateParser.parseStates(context, country, stateResource);
		List<State> managedStates = stateRepository.save(states);
		logger.info("Saved {} states.", managedStates.size());
		State state = stateRepository.findByContextAndStateCode(context, rootEntityStateCode);

		// Cities
		if (state==null) {
			throw new IllegalArgumentException("Please, provide required sate to allow for default city resolution.");
		}
		
		try {
			for (State s: managedStates) {
				Resource cityResource = new ClassPathResource(resolveCityDataPath(country, s));
				List<City> cities = cityParser.parseCities(context, s, cityResource);
				cityRepository.save(cities);
				logger.info("Saved {} cities.", cities.size());	
			}
		} catch (Exception e) {
			logger.info("Error saving cities.");	
		}
		
		City city = cityRepository.findByContextAndCityCode(context, rootEntityCityCode);
		if(city==null){
			throw new IllegalArgumentException("Please, provide required data to allow for default city resolution.");
		}
		return city;
		
	}
	
	/**
	 * Convenient to resolve city files location.
	 * 
	 * @param country
	 * @param state
	 */
	protected String resolveCityDataPath(Country country, State state) {
		return contextDataPath+country.getCountryCode()+"/cities-"+state.getStateCode()+".xml";
	}
	
	/**
	 * Basic prototype creation.
	 * 
	 * @param alias
	 * @param summary
	 * @param type
	 */
	protected Entity createPrototype(String alias, String summary, char type) {
		Entity entity = new Entity();
		entity.setAlias(alias);
		entity.setSummary(summary);
		entity.setEntityType(type);
		return entity;
	}
	
	public Entity installEntity(Operator context, Entity prototype) {
		String contextName = env.getProperty("iservport.defaultContextName", "DEFAULT");
		if (context==null) {
			throw new IllegalArgumentException("Unable to find context");
		}
		Entity entity = entityRepository.findByContextNameAndAlias(contextName, prototype.getAlias());
		if (entity==null) {
			logger.info("Will install entity for context {} and alias {}.", contextName, prototype.getAlias());
			entity = entityRepository.saveAndFlush(new Entity(context, prototype));
		}
		else {
			logger.debug("Found existing entity for context {} and alias {}.", contextName, prototype.getAlias());
		}
		return entity;
	}
	
}
