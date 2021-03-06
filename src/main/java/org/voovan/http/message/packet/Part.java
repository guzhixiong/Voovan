package org.voovan.http.message.packet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.voovan.tools.TObject;

/**
 * HTTP 的 part 对象
 * 	改对象仅在 POST 请求,并且Content-Type = multipart/form-data时使用
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class Part {
	private Header header;
	private Body body;
	
	/**
	 * Part 类型枚举
	 * @author helyho
	 *
	 */
	public enum PartType{
		BINARY,TEXT
	} 
	
	/**
	 * 构造函数
	 */
	public Part(){
		header = new  Header();
		body = new Body();
	}
	
	/**
	 * 构造函数
	 * @param name 参数名
	 * @param value 参数值
	 */
	public Part(String name,String value){
		header = new  Header();
		body = new Body();
		header.put("name", name);
		body.write(value.getBytes());
	}
	
	/**
	 * Part 的 Header 对象
	 * @return
	 */
	public Header header(){
		return header;
	}
	
	/**
	 * Part 的 body 对象
	 * @return
	 */
	public Body body(){
		return body;
	}
	
	/**
	 * 获取 Part 的名称
	 * @return
	 */
	public String getName() {
		return  header.get("name");
	}

	/**
	 * 获取 part 的文件名称
	 * @return
	 */
	public String getFileName() {
		return header.get("filename");
	}

	/**
	 * 获取 Part 内容的类型
	 * @return
	 */
	public PartType getType(){
		if(TObject.nullDefault(header.get("Content-Transfer-Encoding"), "").equals("binary")){
			return PartType.BINARY;
		}
		else{
			return PartType.TEXT;
		}
	}
	
	/**
	 * 将 Part 的内容保存为文件
	 * @param file
	 * @throws IOException
	 */
	public void saveAsFile(File file) throws IOException{
		FileOutputStream fOutputStream  = new FileOutputStream(file);
		fOutputStream.write(body.getBodyBytes());
		fOutputStream.flush();
		fOutputStream.close();
	}
	
	/**
	 * 将 Part 的内容保存为文件
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsFile(String fileName) throws IOException{
		FileOutputStream fOutputStream  = new FileOutputStream(new File(fileName));
		fOutputStream.write(body.getBodyBytes());
		fOutputStream.flush();
		fOutputStream.close();
	}
	
	@Override
	public String toString(){
		header.put("Content-Disposition", "form-data");
		if(header.get("name")!=null){
			header.put("Content-Disposition", header.get("Content-Disposition")+"; name=\""+header.get("name")+"\"");
		}
		if(header.get("filename")!=null){
			header.put("Content-Disposition", header.get("Content-Disposition")+"; filename=\""+header.get("filename")+"\"");
		}
		return header.toString()+"\r\n";
	}
}
