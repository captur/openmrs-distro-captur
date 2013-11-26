/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

/**
 * Audits all UIFR managed page controllers and fragment actions
 */

import org.openmrs.api.context.Context
import org.openmrs.module.ModuleFactory
import org.openmrs.util.OpenmrsClassLoader
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.RegexPatternTypeFilter
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.util.regex.Pattern

/**
 * Fetches a singleton component from the application context
 * @param className the class name
 */
def contextComponents = { className ->
	return Context.getRegisteredComponents(Context.loadClass(className))
}

/**
 * Creates a class path scanner
 * @param regex the class name regex
 */
def scanner = { regex ->
	def scanner = new ClassPathScanningCandidateComponentProvider(false)
	scanner.setResourceLoader(new PathMatchingResourcePatternResolver(OpenmrsClassLoader.instance))
	scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(regex)))
	scanner
}

/**
 * Uncapitalizes a string (e.g. HelloWorld -> helloWorld)
 */
def uncapitalize = {
	it[0].toLowerCase() + it.substring(1)
}

/**
 * Re-constructs a page URL
 */
def pageUrl = { provider, basePkg, clazz ->
	def relPkg = (clazz.package.name - basePkg).replace(".", "/")
	def page = clazz.simpleName.substring(0, clazz.simpleName.length() - 14)
	provider + relPkg + "/" + uncapitalize(page) + ".page"
}

/**
 * Re-constructs an action URL
 */
def actionUrl = { provider, basePkg, clazz, method ->
	def relPkg = (clazz.package.name - basePkg).replace(".", "/")
	def frag = clazz.simpleName.substring(0, clazz.simpleName.length() - 18)
	provider + relPkg + "/" + uncapitalize(frag) + "/" + method.name + ".action"
}

/**
 * Audits the given page controller
 */
def auditPageController = { writer, provider, basePkg, clazz ->
	def url = pageUrl(provider, basePkg, clazz)
	writer.write url + "\n"
}

/**
 * Audits the given fragment controller
 */
def auditFragmentController = { writer, provider, basePkg, clazz ->
	def count = 0

	clazz.methods.each { action ->
		count++
		def url = actionUrl(provider, basePkg, clazz, action)

		writer.write url + "\n"
	}

	count
}

// Load all registered UIFR configurations
def uiconfigs = contextComponents("org.openmrs.ui.framework.StandardModuleUiConfiguration")

// Create output file
def file = File.createTempFile("urls", ".csv")
def writer = new FileWriter(file)

writer.write "URL\n"

uiconfigs.each { uiconfig ->
	def pages = 0, actions = 0
	println "Scanning module: " + uiconfig.moduleId + "..."

	def pagesPkg = "org.openmrs.module." + uiconfig.moduleId + ".page.controller"
	def fragsPkg = "org.openmrs.module." + uiconfig.moduleId + ".fragment.controller"

	scanner("[\\w.]+PageController").findCandidateComponents(pagesPkg).each {
		pages++
		auditPageController(writer, uiconfig.moduleId, pagesPkg, Context.loadClass(it.beanClassName))
	}

	scanner("[\\w.]+FragmentController").findCandidateComponents(fragsPkg).each {
		actions += auditFragmentController(writer, uiconfig.moduleId, fragsPkg, Context.loadClass(it.beanClassName))
	}

	println " > Page controllers:" + pages
	println " > Actions:" + actions
}

println "URL audit log written to " + file.absolutePath