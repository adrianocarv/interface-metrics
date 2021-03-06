
package br.inpe.cap.interfacemetrics.application;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import br.inpe.cap.interfacemetrics.domain.InterfaceMetric;
import br.inpe.cap.interfacemetrics.domain.OccurrencesCombination;
import br.inpe.cap.interfacemetrics.infrastructure.InterfaceMetricPairRepository;
import br.inpe.cap.interfacemetrics.infrastructure.InterfaceMetricRepository;
import br.inpe.cap.interfacemetrics.infrastructure.RepositoryType;
import br.unifesp.ict.seg.geniesearchapi.services.searchaqe.infrastructure.AQEApproach;
import br.unifesp.ict.seg.geniesearchapi.services.searchaqe.infrastructure.QueryTerm;
import br.unifesp.ict.seg.geniesearchapi.services.searchaqe.infrastructure.SourcererQueryBuilder;

public class InterfaceMetricOccurrencesHelper {

	private InterfaceMetric interfaceMetric;
	private AQEApproach aqeApproach;
	private List<InterfaceMetric> occurrences = new ArrayList<InterfaceMetric>();

	private InterfaceMetricRepository repository;
	private InterfaceMetricPairRepository pairRepository;
	private RepositoryType repositoryType;
	
	
	public InterfaceMetricOccurrencesHelper(InterfaceMetric interfaceMetric, RepositoryType repositoryType) throws Exception {
		repository = new InterfaceMetricRepository(repositoryType);
		pairRepository = new InterfaceMetricPairRepository(repositoryType);
		this.repositoryType = repositoryType;

		this.interfaceMetric = interfaceMetric;
		this.aqeApproach = getAQEApproach();
		interfaceMetric.setExpandedParams(this.aqeApproach.getParamsTerms());
	}

	private AQEApproach getAQEApproach() throws Exception {
		boolean relaxReturn = false;
		boolean relaxParams = false;
		boolean contextRelevants = true;
		boolean filterMethodNameTermsByParameter = false;

		String expanders = "WordNet , Type";
		
		String className = interfaceMetric.getClassName();
		String methodName = interfaceMetric.getMethodName();
		String returnType = interfaceMetric.getReturnType();
		String params = interfaceMetric.getParams();
		
		AQEApproach aqeApproach = new AQEApproach(expanders, relaxReturn, relaxParams, contextRelevants, filterMethodNameTermsByParameter);
		aqeApproach.loadMethodInterface(className, methodName, returnType, params);

		return aqeApproach;
	}
	
	public void updateOccurrences() throws Exception {

		pairRepository.deletePairs(interfaceMetric);
		
		this.occurrences = repository.findOccurrences(this);
		
		List<OccurrencesCombination> combinations = OccurrencesCombination.allCombinations();
		for(OccurrencesCombination combination : combinations){
			List<InterfaceMetric> occurrences = this.getOccurrences(combination);
			interfaceMetric.setOccurrencesTotal(combination, occurrences.size());
			pairRepository.insertPairs(interfaceMetric, combination, occurrences);
		}
	}

	List<InterfaceMetric> getOccurrences(OccurrencesCombination combination) {
		List<InterfaceMetric> matches = new ArrayList<InterfaceMetric>();
		for(InterfaceMetric occurence : occurrences){
			if(this.match(combination, occurence))
				matches.add(occurence);
		}
		return matches;
	}

	private boolean match(OccurrencesCombination combination, InterfaceMetric occurence) {
		boolean matchPackage = this.matchPackage(combination, occurence);
		boolean matchClassName = this.matchClassName1(combination, occurence);
		boolean matchMethodName = this.matchMethodName(combination, occurence);
		boolean matchTypes = this.matchTypes(combination, occurence);
		
		return matchPackage && matchClassName && matchMethodName && matchTypes;
	}

	private boolean matchPackage(OccurrencesCombination combination, InterfaceMetric occurence) {
		if (combination.isDifferentPackage())
			return !interfaceMetric.getPackage().equals(occurence.getPackage());
		else
			return interfaceMetric.getPackage().equals(occurence.getPackage());
	}

	//Versão original: Ignora o nome da Classe, ou Considera exatamente o mesmo nome da Classe 
	@SuppressWarnings("unused")
	private boolean matchClassName(OccurrencesCombination combination, InterfaceMetric occurence) {
		return combination.isIgnoreClass() ? true : interfaceMetric.getClassName().equals(occurence.getClassName());
	}

	//Ignora o nome da Classe, ou Considera o nome da Classe usando a expansão Wordnet
	private boolean matchClassName1(OccurrencesCombination combination, InterfaceMetric occurence) {
		if(combination.isIgnoreClass())
			return true;

		List<String> aWords = new ArrayList<String>();
		for(QueryTerm term : aqeApproach.getClassNameTerms()){
			aWords.addAll(term.getExpandedTerms());
		}		

		String[] bWords = occurence.getWordsClassName();
		
		for(String a : aWords)
			for(String b : bWords)
				if(a.equalsIgnoreCase(b))
					return true;
		
		return false;
	}

	//Considera exatamente o mesmo nome do método, ou Considera usando a expansão Wordnet
	private boolean matchMethodName(OccurrencesCombination combination, InterfaceMetric occurence) {
		if(!combination.isExpandMethodName()) // ## sf110_semantic_v1.xlsx
			return interfaceMetric.getMethodName().equals(occurence.getMethodName()); // ## sf110_semantic_v1.xlsx
		//if(combination.isIgnoreMethodName())
			//return true; // ## sf110_semantic_v2.xlsx 

		if (aqeApproach.getMethodNameTerms().size() != occurence.getTotalWordsMethod())
			return false;
		
		boolean match = true;
		for(int i = 0; i < aqeApproach.getMethodNameTerms().size(); i++){
			QueryTerm term = aqeApproach.getMethodNameTerms().get(i);
			match = term.getExpandedTerms().contains(occurence.getWordsMethod()[i]);
			if(!match) break; //add line on 09 aug 2016
		}
		return match;
	}

	//Considera exatamente os mesmos retorno e parâmetros, ou Considera usando a expansão de Tipos
	private boolean matchTypes(OccurrencesCombination combination, InterfaceMetric occurence) {
		boolean matchReturn = false;
		boolean matchParams = false;

		if(!combination.isExpandTypes()){
			matchReturn = interfaceMetric.getReturnType().equals(occurence.getReturnType());
			matchParams = interfaceMetric.isSameParams(occurence.getParamsNames());
		}else{
			matchReturn = aqeApproach.getReturnTypeTerms().get(0).getExpandedTerms().contains(occurence.getReturnType());
			matchParams = interfaceMetric.isSameExpandedParams(occurence.getParamsNames());
		}
		
		return matchReturn && matchParams;
	}

	public String getOccurrencesSQL(String table) throws Exception {
		String sql = "SELECT * FROM " + table;
		
		String inner = repositoryType == RepositoryType.INNER ? "=" : "<>";
		
		sql += "\n" + "where project_id " + inner + interfaceMetric.getProjectId();
		sql += "\n" + "and total_params = " + interfaceMetric.getTotalParams();
		
		sql += "\n" + this.getSimilarReturnSQLClause();
		sql += "\n" + this.getSimilarParamsSQLClause();
		
		return sql;
	}
	
	private String getSimilarReturnSQLClause() throws Exception {
		SourcererQueryBuilder sourcererQueryBuilder = new SourcererQueryBuilder(aqeApproach);
		String sourcererSyntax = sourcererQueryBuilder.getReturnTypePart(aqeApproach.getReturnTypeTerms());
		
		String clause = sourcererSyntax;
		
		clause = StringUtils.replace(clause, "\nreturn_sname_contents:(( ", " and return_type in ('");
		clause = StringUtils.replace(clause, " ))", "')");
		clause = StringUtils.replace(clause, " OR ", "','");
		
		return clause; 
	}

	private String getSimilarParamsSQLClause() throws Exception {
		SourcererQueryBuilder sourcererQueryBuilder = new SourcererQueryBuilder(aqeApproach);
		String sourcererSyntax = sourcererQueryBuilder.getParamsPart(aqeApproach.getParamsTerms());
		
		String clause = sourcererSyntax;
		clause = StringUtils.substringAfterLast(clause, "\n");
		clause = StringUtils.replace(clause, "params_snames_contents:(( ", " and (\n     params like '%");
		clause = StringUtils.replace(clause, " ) AND ( ", "%'\n  or params like '%");
		clause = StringUtils.replace(clause, " OR ", "%'\n  or params like '%");
		clause = StringUtils.replace(clause, " ))", "%'\n)");
		
		return clause; 
	}

	//Accessors
	public List<InterfaceMetric> getOccurrences() {
		return occurrences;
	}

	public AQEApproach getAqeApproach() {
		return aqeApproach;
	}

	public InterfaceMetric getInterfaceMetric() {
		return interfaceMetric;
	}
}
