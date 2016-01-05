package edu.isi.bmkeg.uimaBioC.elasticSearch;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

public class ViewConverter {

	Set<Class<? extends Object>> viewClasses;
	
	public <T> ViewConverter(T viewObject) throws Exception {
		Class c = viewObject.getClass();
		Package p = c.getPackage();
		
		List<ClassLoader> classLoadersList = new LinkedList<ClassLoader>();
		classLoadersList.add(ClasspathHelper.contextClassLoader());
		classLoadersList.add(ClasspathHelper.staticClassLoader());

		Collection<URL> jarFiles = ClasspathHelper.forClassLoader(classLoadersList.toArray(new ClassLoader[2]));
		List<URL> vpdmfFiles = new ArrayList<URL>();
		for(URL url : jarFiles) {
			if( url.toString().contains("vpdmf-") ){
				vpdmfFiles.add(url);
			}
		}
		
		//
		// Avoid annoying information from 
		// reflections about the classpath 
		// by redirecting STDOUT to null. 
		//
		PrintStream original = System.out;
	    System.setOut(new PrintStream(new OutputStream() {
	                public void write(int b) {
	                    //DO NOTHING
	                }
	            }));
		Reflections reflections = new Reflections(new ConfigurationBuilder()
			    .setScanners(new SubTypesScanner(false /* don't exclude Object.class */), 
			    new ResourcesScanner())
			    .setUrls(vpdmfFiles)
			    .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix( p.getName() ))
			    		));
	    System.setOut(original);
		
		viewClasses = reflections.getSubTypesOf(Object.class);
	}
	
	public <T,X> T baseObjectToView(Object baseObject, T viewObject) throws Exception {

		Map<String, Method> viewMethods = getMethodsLookup(viewObject.getClass());
		Map<String, Method> baseMethods = getMethodsLookup(baseObject.getClass());

		for( String methodName : viewMethods.keySet() ) {
			if( !methodName.startsWith("set") ) 
				continue;
			
			Method setter = viewMethods.get(methodName);
			Method getter = baseMethods.get(methodName.replaceFirst("set", "get"));
			if( getter == null ) 
				getter = baseMethods.get(methodName.replaceFirst("set", "getIs"));

			Object o = getter.invoke(baseObject);
			
			if( o == null ) {
				continue;
			}
				
			Class c = o.getClass();
			Class cc = setter.getParameterTypes()[0];
			if( c.equals(cc) ) {
				
				setter.invoke(viewObject, o);

			} else if(o instanceof List) {
			
				Type baseT = ((ParameterizedType) getter.getGenericReturnType())
						.getActualTypeArguments()[0];
				Type viewT = ((ParameterizedType) setter.getGenericParameterTypes()[0])
						.getActualTypeArguments()[0];
				
				Class<X> clazz = (Class<X>) getClass(viewT);
				List<X> ll = new ArrayList<X>();
				
				for( Object lo : (List) o ) {						
					Object loo = clazz.newInstance();
					loo = this.baseObjectToView(lo, loo);
					ll.add((X) loo);
				}
				setter.invoke(viewObject, ll);
				
			} else {
				
				try{
				
					Object oo = cc.newInstance();
					oo = this.baseObjectToView(o, oo);
					setter.invoke(viewObject, oo);
				
				} catch (InstantiationException e) {
					
					Constructor cons = c.getConstructor(new Class[]{cc});
					Object o2 = cons.newInstance(o);
					setter.invoke(viewObject, o2);

				}
			}

		}
		
		return viewObject;
		
	}
	
	private static Map<String, Method> getMethodsLookup(Class c) {
		Map<String, Method> methods = new HashMap<String, Method>();
		Method mArray[] = c.getMethods();
		for (int i = 0; i < mArray.length; i++) {
			Method m = mArray[i];
			String mName = m.getName();
			methods.put(mName, m);
		}
		return methods;
	}

	private static final String TYPE_NAME_PREFIX = "class ";
	 
	public static String getClassName(Type type) {
	    if (type==null) {
	        return "";
	    }
	    String className = type.toString();
	    if (className.startsWith(TYPE_NAME_PREFIX)) {
	        className = className.substring(TYPE_NAME_PREFIX.length());
	    }
	    return className;
	}
	 
	public static Class<?> getClass(Type type) 
	            throws ClassNotFoundException {
	    String className = getClassName(type);
	    if (className==null || className.isEmpty()) {
	        return null;
	    }
	    return Class.forName(className);
	}
	
}
