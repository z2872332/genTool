package tool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.beetl.core.Configuration;
import org.beetl.core.GroupTemplate;
import org.beetl.core.Template;
import org.beetl.core.resource.ClasspathResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 代码生成工具（参考：http://note.youdao.com/share/?id=45c90ddb99a119aa31be157363511ebe&type=note）
 * @author 钟世云
 */
public class GenTool {
	public static final String url = "jdbc:mysql://192.168.100.98:3306/information_schema";
	public static final String user = "jiwudev";
	public static final String pass = "jiwudev"; 
	
	public static final String rootPath = "E:\\zsyTool";
	private String tableName = null;
	private Map<String, Object> meteData = null;
	
	public GenTool(String tableName) {
		this.tableName = tableName;
		meteData = getMeteData(tableName);
	}

	public Map<String, Object> getMeteData(String tableName) {
		Map<String, Object> result = new HashMap<String, Object>();
		String table_schema = "flt";
		String strsql = "SELECT column_name,data_type,column_comment, column_key, is_nullable, column_default FROM COLUMNS WHERE table_schema='"
				+ table_schema + "' and table_name='" + tableName + "' ";
		String tableSql = "select * from information_schema.`TABLES` a where a.TABLE_SCHEMA='" + table_schema + "' and a.TABLE_NAME='" + tableName + "'";
		
		Connection conn = null;
		try {
			conn = getConnection();
			
			PreparedStatement pstmtTable = conn.prepareStatement(tableSql);
			ResultSet resTable = (ResultSet) pstmtTable.executeQuery();
			if(resTable.next()) {
				result.put("table_comment", resTable.getString("table_comment"));
			}
			
			PreparedStatement pstmt = conn.prepareStatement(strsql);
			ResultSet res = (ResultSet) pstmt.executeQuery();

			List<Map<String, Object>> fieldList = new ArrayList<Map<String, Object>>();
			Map<String, Object> pri = new HashMap<String, Object>();
			while (res.next()) {
				Map<String, Object> map = new HashMap<String, Object>();
				String column_name = res.getString("column_name");
				map.put("column_name", column_name);
				map.put("column_name_upper", column_name.substring(0, 1).toUpperCase() + column_name.substring(1, column_name.length()));
				map.put("data_type", sqlType2JavaType(res.getString("data_type")));
				map.put("column_key", res.getString("column_key"));
				map.put("is_nullable", res.getString("is_nullable"));
				map.put("column_default", res.getString("column_default"));
				//注释扩展字段
				String columnComment = res.getString("column_comment");
				if(columnComment != null && columnComment.endsWith("}") && columnComment.indexOf("{") > 0) {
					try {
						map.put("column_comment", columnComment.substring(0, columnComment.indexOf("{")));
						String xAttr = columnComment.substring(columnComment.indexOf("{"), columnComment.length());
						ObjectMapper mapper = new ObjectMapper();
						@SuppressWarnings("unchecked")
						Map<String, String> xAttrMap = mapper.readValue(xAttr, Map.class);
						System.out.println(xAttrMap);
						
						String dictStr = xAttrMap.get("dict");
						if(isNotEmpty(dictStr)) {
							String[] dicts = dictStr.split(" *, *");
							Map<String, String> dict = new LinkedHashMap<String, String>();
							for (int i = 0; i < dicts.length; i += 2) {
								dict.put(dicts[i], dicts[i + 1]);
							}
							map.put("dict", dict);
						}
						map.put("input_type", xAttrMap.get("input_type"));
						map.put("is_show_list", xAttrMap.get("is_show_list"));
						map.put("is_search", xAttrMap.get("is_search"));
					} catch (Exception e) {
						System.err.println("注释扩展字段解析失败！");
						e.printStackTrace(); 
					}
				}else {
					map.put("column_comment", columnComment);
				}
				
				
				if("PRI".equals(res.getString("column_key"))) {
					pri = map;
				}
				fieldList.add(map);
				System.out.println(map);
			}
			if (fieldList.size() == 0) {
				throw new RuntimeException("没有找到表：" + tableName);
			}
			List<Map<String, Object>> addFieldList = new ArrayList<Map<String, Object>>();
			for (Map<String, Object> map : fieldList) {
				if(!"PRI".equals(map.get("column_key")) || !"CURRENT_TIMESTAMP".equals(map.get("column_default"))) {
					addFieldList.add(map);
				}
			}
			result.put("catalog", table_schema);
			result.put("tableName", tableName);
			result.put("className", this.initcap(tableName));
			result.put("fieldList", fieldList);
			result.put("addFieldList", addFieldList);
			result.put("pri", pri);	//主键
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public boolean isNotEmpty(String str) {
		boolean result = true;
		if(str == null) {
			return false;
		}
		str = str.trim();
		if("".equals(str)) {
			return false;
		}
		return result;
	}
	
	public void generate(String tempFileName, String classFileName) throws IOException {
		ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader();
		Configuration cfg = Configuration.defaultConfiguration();
		GroupTemplate gt = new GroupTemplate(resourceLoader, cfg);
		Template t = gt.getTemplate("/template/" + tempFileName);
        t.binding(meteData);
		//System.out.println(t.render());
        
        //创建jsp文件夹
        String jspPath = rootPath + File.separator + meteData.get("tableName");
        File jpsFile = new File(jspPath);
		if(jpsFile.exists() == false) {
			jpsFile.mkdirs();
		}
		
		FileWriter fw = null;
		if(classFileName.endsWith(".jsp")) {
			fw = new FileWriter(jspPath + File.separator + classFileName);
		}else {
			fw = new FileWriter(rootPath + File.separator + classFileName);
		}
		t.renderTo(fw);
		fw.flush();
		fw.close();
	}
	
	public void genEntity() throws IOException {
		generate("entity.html", this.initcap(tableName) + ".java");
	}
	
	public void genDao() throws IOException {
		generate("dao.html", this.initcap(tableName) + "DAO.java");
	}
	
	public void genIManager() throws IOException {
		generate("iManager.html", "I" + this.initcap(tableName) + "Manager.java");
	}
	
	public void genManagerImpl() throws IOException {
		generate("managerImpl.html", this.initcap(tableName) + "ManagerImpl.java");
	}
	
	public void genController() throws IOException {
		generate("controller.html", this.initcap(tableName) + "Controller.java");
	}
	
	public void genJspList() throws IOException {
		generate("jsp_list.html", "list.jsp");
	}
	
	public void genJspAdd() throws IOException {
		generate("jsp_add.html", "add.jsp");
	}
	
	public void genJspUpdate() throws IOException {
		generate("jsp_update.html", "update.jsp");
	}
	
	
	private String sqlType2JavaType(String sqlType) {
        if (sqlType.equalsIgnoreCase("bit")) {
            return "bool";
        } else if (sqlType.equalsIgnoreCase("tinyint")) {
            return "byte";
        } else if (sqlType.equalsIgnoreCase("smallint")) {
            return "short";
        } else if (sqlType.equalsIgnoreCase("int")
        		||sqlType.equalsIgnoreCase("integer")) {
            return "Integer";
        } else if (sqlType.equalsIgnoreCase("bigint")) {
            return "long";
        } else if (sqlType.equalsIgnoreCase("float")) {
            return "float";
        } else if (sqlType.equalsIgnoreCase("decimal")
                || sqlType.equalsIgnoreCase("numeric")
                || sqlType.equalsIgnoreCase("real")) {
            return "double";
        } else if (sqlType.equalsIgnoreCase("money")
                || sqlType.equalsIgnoreCase("smallmoney")
                || sqlType.equalsIgnoreCase("double")) {
            return "double";
        } else if (sqlType.equalsIgnoreCase("varchar")
                || sqlType.equalsIgnoreCase("char")
                || sqlType.equalsIgnoreCase("nvarchar")
                || sqlType.equalsIgnoreCase("nchar")) {
            return "String";  
        } else if (sqlType.equalsIgnoreCase("datetime")
        		|| sqlType.equalsIgnoreCase("date")
        		|| sqlType.equalsIgnoreCase("timestamp")) {
            return "String";  
        }  
  
        else if (sqlType.equalsIgnoreCase("image")) {
            return "Blob";  
        } else if (sqlType.equalsIgnoreCase("text")) {
            return "Clob";
        }
        return null;
    }
	
	private String initcap(String str) {  
        char[] ch = str.toCharArray();  
        if (ch[0] >= 'a' && ch[0] <= 'z') {  
            ch[0] = (char) (ch[0] - 32);  
        }  
        return new String(ch);  
    }
	
	public static void main(String[] args) throws IOException, SQLException {
		String tableName = null;
		tableName = "menuconfig";
    	/*System.out.println("请输入表名：");
    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    	tableName = String.valueOf(br.readLine());*/
		GenTool t = new GenTool(tableName);
		t.genEntity();
		t.genDao();
		t.genIManager();
		t.genManagerImpl();
		t.genController();
		t.genJspList();
		t.genJspAdd();
		t.genJspUpdate();
		
		System.err.println("Y(^_^)Y 生成成功，文件保存在->" + rootPath);
	}
	
	public static Connection getConnection() {
		Connection conn = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			conn = DriverManager.getConnection(url, user, pass);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conn;
	}
	
}
