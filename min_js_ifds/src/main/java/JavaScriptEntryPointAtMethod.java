//import com.ibm.wala.cast.ipa.callgraph.ScriptEntryPoints;
//import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptEntryPoints;
//import com.ibm.wala.cast.js.types.JavaScriptTypes;
//import com.ibm.wala.cast.loader.DynamicCallSiteReference;
//import com.ibm.wala.classLoader.CallSiteReference;
//import com.ibm.wala.classLoader.IClassLoader;
//import com.ibm.wala.classLoader.IMethod;
//import com.ibm.wala.ipa.cha.IClassHierarchy;
//import com.ibm.wala.types.TypeName;
//
//public class JavaScriptEntryPointAtMethod extends JavaScriptEntryPoints {
//
////    @Override
////    protected CallSiteReference makeScriptSite(IMethod m, int pc) {
////        return new DynamicCallSiteReference(JavaScriptTypes.CodeBody, pc);
////    }
////
////    public JavaScriptEntryPointAtMethod(String className, IClassHierarchy cha, IClassLoader loader) {
////        super(cha, loader.lookupClass(TypeName.findOrCreate(className)));
////    }
//
//
//    @Override
//    protected boolean keep() {
//        return super.keep();
//    }
//}
