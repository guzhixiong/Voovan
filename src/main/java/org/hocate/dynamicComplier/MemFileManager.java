package org.hocate.dynamicComplier;

import java.io.IOException;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import org.hocate.log.Logger;

public class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {

	private JavaMemClass javaMemClass;
	protected MemFileManager(JavaFileManager fileManager) {
		super(fileManager);
	}
	
	public JavaMemClass getJavaMemClass() {
		return javaMemClass;
	}
	
	@Override
	public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location,
            String className,
            JavaFileObject.Kind kind,
            FileObject sibling)
     throws IOException{
		javaMemClass = new JavaMemClass(className, kind);
		return javaMemClass;
		
	}
}
