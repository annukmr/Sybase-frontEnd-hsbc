package com.iig.gcp.extraction.syabse.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.Principal;
import java.util.ArrayList;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.iig.gcp.CustomAuthenticationProvider;
import com.iig.gcp.extraction.sybase.dto.ConnectionMaster;
import com.iig.gcp.extraction.sybase.dto.CountryMaster;
import com.iig.gcp.extraction.sybase.dto.DataDetailBean;
import com.iig.gcp.extraction.sybase.dto.DriveMaster;
import com.iig.gcp.extraction.sybase.dto.RunFeedsBean;
import com.iig.gcp.extraction.sybase.dto.SourceSystemDetailBean;
import com.iig.gcp.extraction.sybase.dto.SourceSystemMaster;
import com.iig.gcp.extraction.sybase.dto.TargetMaster;
import com.iig.gcp.extraction.sybase.dto.TempDataDetailBean;
import com.iig.gcp.extraction.sybase.service.ExtractionService;

@Controller
@SessionAttributes(value = { "user_name", "project_name", "jwt" })
public class ExtractionController {

	private static final Logger logger = LogManager.getLogger(ExtractionController.class);

	@Value("${sybase.create.micro.service.url}")
	private String sybaseBackendUrl;
	

	@Value("${target.micro.service.url}")
	private String targetComputeUrl;
	
	@Value("${feed.micro.service.url}")
	private String feedComputeUrl;

	@Value("${schedular.micro.service.url}")
	private String schedularComputeUrl;

	
	@Value("${parent.front.micro.services}")
	private String parentMs;

	@Autowired
	private ExtractionService es;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Value("${parent.front.micro.services}")
	private String parentMicroServices;
	
	static final String SOURCE;
	static {SOURCE="Sybase";}


	@GetMapping(value = { "/", "/login" })
	public ModelAndView extractionHome(@Valid @ModelAttribute("jsonObject") final String jsonObject,final ModelMap model,final HttpServletRequest request) throws JSONException {
		JSONObject jObj = new JSONObject(jsonObject);
		String user_name = jObj.getString("userId");
		String project_name = jObj.getString("project");
		String jwt = jObj.getString("jwt");

		// Validate the token at the first place
		try {
			JSONObject jsonModelObject = null;
			if (jwt == null || jwt.equals("")) {

				return new ModelAndView("/login");
			}
			authenticationByJWT(user_name + ":" + project_name, jwt);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return new ModelAndView("/login");
			// redirect to Login Page
		}

		request.getSession().setAttribute("user_name", user_name);
		request.getSession().setAttribute("project_name", project_name);
		request.getSession().setAttribute("jwt", jwt);

		model.addAttribute("user_name", user_name);
		model.addAttribute("project", project_name);
		return new ModelAndView("/index");
	}

	
	private void authenticationByJWT(final String name,final String token) {
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(name, token);
		Authentication authenticate = this.authenticationManager.authenticate(authToken);
		SecurityContextHolder.getContext().setAuthentication(authenticate);
	}

	@RequestMapping(value = { "/parent" }, method = RequestMethod.GET)
	public ModelAndView parentHome(final ModelMap modelMap,final HttpServletRequest request,final Authentication auth) throws JSONException {
		CustomAuthenticationProvider.MyUser m = (CustomAuthenticationProvider.MyUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("userId", m.getName());
		jsonObject.put("project", m.getProject());
		jsonObject.put("jwt", m.getJwt());
		modelMap.addAttribute("jsonObject", jsonObject.toString());
		return new ModelAndView("redirect:" + "//" + this.parentMicroServices + "/fromChild", modelMap);


	}

	
	@RequestMapping(value = "/extraction/Event", method = RequestMethod.GET)
	public ModelAndView Event() {
		return new ModelAndView("extraction/Event");
	}

	///extraction/ConnectionDetailsSybase
	
	@GetMapping(value="/extraction/ConnectionDetailsSybase")
	//@RequestMapping(value = "/extraction/ConnectionDetailsSybase", method = RequestMethod.GET)
	public ModelAndView ConnectionDetails(final ModelMap model,final HttpServletRequest request) {
		
		model.addAttribute("src_val", SOURCE);
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));

		
		ArrayList<String> system;
		try {
			system = this.es.getSystem((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("system", system);
			ArrayList<ConnectionMaster> conn_val = this.es.getConnections(SOURCE, (String) request.getSession().getAttribute("project_name"));
			model.addAttribute("conn_val", conn_val);
			ArrayList<DriveMaster> drive =this.es
			.getDrives((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("drive", drive);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/ConnectionDetailsSybase");
	}

	//after TEST n SAVE
	@RequestMapping(value = "/extraction/ConnectionDetails1", method = RequestMethod.POST)
	public ModelAndView ConnectionDetails1(@Valid @ModelAttribute("x") String x, @ModelAttribute("src_val") final String src_val, @ModelAttribute("button_type") final String button_type,final ModelMap model,
		final HttpServletRequest request) throws UnsupportedOperationException, Exception {
		String resp = null;
		
		
					
		JSONObject jsonObject = new JSONObject(x);
		jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String) request.getSession().getAttribute("jwt"));
		x = jsonObject.toString();
		resp = this.es.invokeRest(x, sybaseBackendUrl + button_type);
		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			model.addAttribute("successString", final_message);
		}
		model.addAttribute("src_val", SOURCE);
		// UserAccount u = (UserAccount) 
		// request.getSession().getAttribute("user");
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		ArrayList<String> system = this.es.getSystem((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("system", system);
		ArrayList<ConnectionMaster> conn_val = this.es.getConnections(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("conn_val", conn_val);
		ArrayList<DriveMaster> drive = this.es.getDrives((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("drive", drive);
		return new ModelAndView("extraction/ConnectionDetailsSybase");
	}

	
	
	
	@RequestMapping(value = "/extraction/ConnectionDetailsEdit", method = RequestMethod.POST)
	public ModelAndView ConnectionDetailsEdit(@Valid @ModelAttribute("conn") int conn, @ModelAttribute("src_val") final String src_val,final ModelMap model,final HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		ConnectionMaster conn_val = this.es.getConnections2(src_val, conn, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("conn_val", conn_val);
		model.addAttribute("src_val", src_val);
		
		
		
		ArrayList<DriveMaster> drive = this.es.getDrives((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("drive", drive);
		return new ModelAndView("extraction/ConnectionDetailsEditSybase");
	}

	@RequestMapping(value = "/extraction/TargetDetails", method = RequestMethod.GET)
	public ModelAndView TargetDetails(@Valid ModelMap model,final HttpServletRequest request) {
		ArrayList<TargetMaster> tgt;
		try {
			 
			
			tgt = this.es.getTargets((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("usernm", request.getSession().getAttribute("user_name"));
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
			ArrayList<String> system = this.es.getSystem((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("system", system);
			model.addAttribute("tgt_val", tgt);
			ArrayList<DriveMaster> drive = this.es.getDrives((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("drive", drive);
			ArrayList<String> tproj = this.es.getGoogleProject((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("tproj", tproj);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/TargetDetails");
	}

	
	
	@RequestMapping(value = "/extraction/TargetDetails0", method = RequestMethod.POST)
	public ModelAndView TargetDetails0(@Valid final ModelMap model, @ModelAttribute("project1") final String project1, HttpServletRequest request) {
		ArrayList<String> str;
		try {
			str = this.es.getServiceBucket(project1, (String) request.getSession().getAttribute("project_name"));

			ArrayList<String> sa = new ArrayList<String>();
			ArrayList<String> buck = new ArrayList<String>();
			for (int i = 0; i < str.size(); i++) {
				String y = (String) str.get(i);
				String[] x = y.split("\\|");
				sa.add(x[1]);
				buck.add(x[0]);
			}
			model.addAttribute("sa", sa);
			model.addAttribute("buck", buck);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/TargetDetails0");
	}

	
	@RequestMapping(value = "/extraction/TargetDetails1", method = RequestMethod.POST)
	public ModelAndView TargetDetails1(@Valid @ModelAttribute("x") final String x, @ModelAttribute("button_type") final String button_type,final ModelMap model,final HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		String resp = null;
		resp = this.es.invokeRest(x, targetComputeUrl + button_type);
		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			model.addAttribute("successString", final_message);
			model.addAttribute("next_button_active", "active");
		}
		
		ArrayList<TargetMaster> tgt = this.es.getTargets((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		ArrayList<String> system = this.es.getSystem((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("system", system);
		model.addAttribute("tgt_val", tgt);
		
		ArrayList<DriveMaster> drive = this.es.getDrives((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("drive", drive);
		ArrayList<String> tproj = this.es.getGoogleProject((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("tproj", tproj);
		return new ModelAndView("extraction/TargetDetails");
	}

	@RequestMapping(value = "/extraction/TargetDetailsEdit", method = RequestMethod.POST)
	public ModelAndView TargetDetailsEdit(@Valid @ModelAttribute("tgt") final int tgt,final ModelMap model,final HttpServletRequest request) {
		TargetMaster tgtx;
		try {
			tgtx = this.es.getTargets1(tgt);
			model.addAttribute("tgtx", tgtx);
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
			ArrayList<String> tproj = this.es.getGoogleProject((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("tproj", tproj);
			ArrayList<DriveMaster> drive = this.es.getDrives((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("drive", drive);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/TargetDetailsEdit");
	}

	
	
	
	@RequestMapping(value = "/extraction/SystemDetails", method = RequestMethod.GET)
	public ModelAndView SystemDetails(final ModelMap model,final HttpServletRequest request) {
		model.addAttribute("src_val", SOURCE);
		ArrayList<SourceSystemMaster> src_sys_val;
		try {
			src_sys_val = this.es.getSources(SOURCE, (String) request.getSession().getAttribute("project_name"));
			model.addAttribute("src_sys_val", src_sys_val);
						
			ArrayList<ConnectionMaster> conn_val = this.es.getConnections(this.SOURCE, (String) request.getSession().getAttribute("project_name"));
			model.addAttribute("conn_val", conn_val);
			ArrayList<TargetMaster> tgt = this.es.getTargets((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("tgt", tgt);
			model.addAttribute("usernm", request.getSession().getAttribute("user_name"));
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
			ArrayList<CountryMaster> countries = this.es.getCountries();
			model.addAttribute("countries", countries);
		} catch (Exception e) {

			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/SystemDetails");
	}

	
	
	@RequestMapping(value = "/extraction/SystemDetails1", method = RequestMethod.POST)
	public ModelAndView SystemDetails1(@Valid @RequestParam(value = "sun", required = true) final String sun,final ModelMap model) throws UnsupportedOperationException, Exception {
		int stat = this.es.checkNames(sun);
		model.addAttribute("stat", stat);
		return new ModelAndView("extraction/SystemDetails1");
	}

	
	@RequestMapping(value = "/extraction/SystemDetails2", method = RequestMethod.POST)
	public ModelAndView SystemDetails2(@Valid @ModelAttribute("src_val") final String src_val, @ModelAttribute("x") String x, @ModelAttribute("button_type") final String button_type, final ModelMap model,
			final HttpServletRequest request) throws UnsupportedOperationException, Exception {
		String resp = null;
		JSONObject jsonObject = new JSONObject(x);
		jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String) request.getSession().getAttribute("jwt"));
		x = jsonObject.toString();
		resp = this.es.invokeRest(x, feedComputeUrl + button_type);
		model.addAttribute("usernm", request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			model.addAttribute("successString", final_message);
			model.addAttribute("next_button_active", "active");
		}
		model.addAttribute("src_val", src_val);
		ArrayList<SourceSystemMaster> src_sys_val = this.es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("src_sys_val", src_sys_val);
		ArrayList<ConnectionMaster> conn_val = this.es.getConnections(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("conn_val", conn_val);
		ArrayList<TargetMaster> tgt = this.es.getTargets((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("tgt", tgt);
		/*
		ArrayList<String> buckets = DBUtils.getBuckets();
		 * model.addAttribute("buckets", buckets);
		 */
		ArrayList<CountryMaster> countries = this.es.getCountries();
		model.addAttribute("countries", countries);
		/*
		 * ArrayList<ReservoirMaster> reservoir = es.getReservoirs();
		 * model.addAttribute("reservoir", reservoir);
		 */
		return new ModelAndView("extraction/SystemDetails");
	}

	
	
	
	
	@RequestMapping(value = "/extraction/SystemDetailsEdit", method = RequestMethod.POST)
	public ModelAndView SystemDetailsEdit(@Valid @ModelAttribute("src_sys") final int src_sys, @ModelAttribute("src_val") final String src_val,final ModelMap model,final HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		ArrayList<SourceSystemDetailBean> ssm = this.es.getSources1(src_val, src_sys);
		model.addAttribute("ssm", ssm);
		ArrayList<ConnectionMaster> conn_val = this.es.getConnections(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("conn_val", conn_val);
		ArrayList<TargetMaster> tgt = this.es.getTargets((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("tgt", tgt);
		ArrayList<CountryMaster> countries = this.es.getCountries();
		model.addAttribute("countries", countries);
		model.addAttribute("src_val", src_val);
		return new ModelAndView("extraction/SystemDetailsEdit");
	}

	
	
	@RequestMapping(value = "/extraction/DataDetails", method = RequestMethod.GET)
	public ModelAndView DataDetails(ModelMap model, HttpServletRequest request) throws IOException {
		
		try {
				
			model.addAttribute("src_val", ExtractionController.SOURCE);
			ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val2 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val = es.getSources("Sybase", (String) request.getSession().getAttribute("project_name"));
			for (SourceSystemMaster ssm : src_sys_val) {
				if ((ssm.getFile_list() == null || ssm.getFile_list().isEmpty()) && (ssm.getTable_list() == null || ssm.getTable_list().isEmpty())
						&& (ssm.getDb_name()==null || ssm.getFile_list().isEmpty())) // 3rd Added for Hive 
					{
					src_sys_val1.add(ssm);
				} else {
					src_sys_val2.add(ssm);
				}
			}
			
			model.addAttribute("src_sys_val1", src_sys_val1);
			model.addAttribute("src_sys_val2", src_sys_val2);
			model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		} catch (Exception e) {

			logger.error(e.getMessage());
		}
		

		
		return new ModelAndView("extraction/DataDetails" +SOURCE);
			}

	@RequestMapping(value = "/extraction/DataDetailsSybase0", method = RequestMethod.POST)
	public ModelAndView DataDetails0(@Valid @ModelAttribute("src_sys_id") int src_sys_id, @ModelAttribute("src_val") String src_val, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		String db_name = null;
		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
		
		 
		
		//ConnectionMaster conn_val = es.getConnections1("Sybase", src_sys_id);
		model.addAttribute("conn_val", conn_val);
		//ArrayList<String> schema_name = es.getSchema("Sybase", conn_val.getConnection_id(), (String) request.getSession().getAttribute("project_name"), db_name);
		
		
		
	ArrayList<String> schema_name = es.getSchema(src_val, conn_val.getConnection_id(), (String) request.getSession().getAttribute("project_name"), db_name);
		
	 
	
		model.addAttribute("schema_name", schema_name);
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		return new ModelAndView("extraction/DataDetailsSybase0");
	}

	@RequestMapping(value = "/extraction/DataDetailsSybase1", method = RequestMethod.POST)
	public ModelAndView DataDetails1(@Valid @ModelAttribute("id") String id, @ModelAttribute("src_sys_id") int src_sys_id, @ModelAttribute("src_val") String src_val,
			@ModelAttribute("schema_name") String schema_name, ModelMap model, HttpServletRequest request) throws UnsupportedOperationException, Exception {
		String href1 = es.getBulkDataTemplate(src_sys_id);
		String db_name = null;
		
		href1 = "field.xls";
		model.addAttribute("href1", href1);
		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
		model.addAttribute("conn_val", conn_val);
		String ext_type = es.getExtType(src_sys_id);
		model.addAttribute("ext_type", ext_type);
		ArrayList<String> tables = es.getTables(src_val, conn_val.getConnection_id(), schema_name, (String) request.getSession().getAttribute("project_name"), db_name);
		model.addAttribute("tables", tables);
		model.addAttribute("schema_name", schema_name);
		model.addAttribute("src_sys_id", src_sys_id);
		model.addAttribute("id", id);
		return new ModelAndView("extraction/DataDetailsSybase1");
	}

	@RequestMapping(value = "/extraction/DataDetailsSybase2", method = RequestMethod.POST)
	public ModelAndView DataDetails2(@Valid @ModelAttribute("id") String id, @ModelAttribute("src_val") String src_val, @ModelAttribute("table_name") String table_name,
			@ModelAttribute("connection_id") int connection_id, @ModelAttribute("schema_name") String schema_name, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		String db_name = null;
		ArrayList<String> fields = es.getFields(id, src_val, table_name, connection_id, schema_name, (String) request.getSession().getAttribute("project_name"), db_name);
//		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
//		ArrayList<String> src_tbl_sch = es.getSchema(src_val, conn_val.getConnection_id(), (String) request.getSession().getAttribute("project_name"));
//		model.addAttribute("src_tbl_sch", src_tbl_sch);
		model.addAttribute("fields", fields);
		model.addAttribute("id", id);

		return new ModelAndView("extraction/DataDetailsSybase2");
	}
	
	@RequestMapping(value = "/extraction/DataDetailsSybase22", method = RequestMethod.POST)
	public ModelAndView DataDetails22(@Valid @ModelAttribute("id") String id, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		model.addAttribute("fields", "");
		model.addAttribute("id", id);
		return new ModelAndView("extraction/DataDetailsSybase2");
	}


	@RequestMapping(value = "/extraction/DataDetailsEditSybase", method = RequestMethod.POST)
	public ModelAndView DataDetailsEdit(@Valid @ModelAttribute("src_sys_id") int src_sys_id, @ModelAttribute("src_val") String src_val, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		int rec_count = es.getRecCount(src_sys_id);
		model.addAttribute("rec_count", rec_count);
		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
		model.addAttribute("conn_val", conn_val);
		String href1 = "/assets/sybase/Juniper_Extraction_Bulk_Upload_Template.xlsm";
		model.addAttribute("href1", href1);
		String href2 = "/assets/sybase/Juniper_Extraction_Bulk_Upload_Template.xlsm";
		model.addAttribute("href2", href2);
		return new ModelAndView("extraction/DataDetailsEditSybase");
	}

	@RequestMapping(value = "/extraction/ExtractData", method = RequestMethod.GET)
	public ModelAndView ExtractData(final ModelMap model,final HttpServletRequest request) throws IOException {
		try {
			model.addAttribute("src_val", SOURCE);
			ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val;
			
			
			
			src_sys_val = this.es.getSources(this.SOURCE, (String) request.getSession().getAttribute("project_name"));
		
			for (SourceSystemMaster ssm : src_sys_val) {
				
				if ((ssm.getFile_list() == null || ssm.getFile_list().isEmpty()) && (ssm.getTable_list() == null || ssm.getTable_list().isEmpty())
						&& (ssm.getDb_name() == null || ssm.getDb_name().isEmpty())) {
					;// 3rd Added for Hive 
				} else {
					src_sys_val1.add(ssm);
				}
			}			
			model.addAttribute("src_sys_val", src_sys_val);
			model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		} catch (Exception e) {

			logger.error(e.getMessage());

		}
		
		return new ModelAndView("extraction/ExtractData");
	}

	@RequestMapping(value = "/extraction/ExtractData1", method = RequestMethod.POST)
	public ModelAndView ExtractData1(@Valid @ModelAttribute("feed_name") final String feed_name, @ModelAttribute("src_val") String src_val,final ModelMap model,final HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		String ext_type = this.es.getExtType1(feed_name);
		if (ext_type.equals("Real") || ext_type.equals("Batch") || ext_type.equals("Event")) {
			model.addAttribute("ext_type", ext_type);
		} else {
			model.addAttribute("errorString", "Job already ordered " + ext_type);
		}
		/*
		 * String ext_type = es.getExtType1(feed_name); model.addAttribute("ext_type",
		 * ext_type);
		 */
		ArrayList<String> kafka_topic = this.es.getKafkaTopic();
		model.addAttribute("kafka_topic", kafka_topic);
		return new ModelAndView("extraction/ExtractData1");
	}

	
	@RequestMapping(value = "/extraction/ExtractData2", method = RequestMethod.POST)
	public ModelAndView ExtractData2(@Valid @ModelAttribute("feed_name") final String feed_name, @ModelAttribute("src_val") final String src_val, @ModelAttribute("x") String x,
			@ModelAttribute("ext_type") final String ext_type, final ModelMap model, final HttpServletRequest request) throws UnsupportedOperationException, Exception {
		String resp = null;
		JSONObject jsonObject = new JSONObject(x);
		jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String) request.getSession().getAttribute("jwt"));
		x = jsonObject.toString();
		
		// if (ext_type.equalsIgnoreCase("Batch")) {
		resp = this.es.invokeRest(x, schedularComputeUrl + "createDag");
		this.es.updateLoggerTable(feed_name);
		// } else {
		// resp = es.invokeRest(x, "extractData");
		// }
		String status0[] = resp.toString().split(":");
		
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
	
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			model.addAttribute("successString", final_message);
			model.addAttribute("next_button_active", "active");
		}
		model.addAttribute("src_val", src_val);
		ArrayList<SourceSystemMaster> src_sys_val = this.es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("src_sys_val", src_sys_val);
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
				
		return new ModelAndView("extraction/ExtractData");
	}

	
	
	
	
	@RequestMapping(value = "/extraction/ExtractData3", method = RequestMethod.POST)
	public ModelAndView ExtractData3(@Valid @ModelAttribute("feed_name") final String feed_name, @ModelAttribute("src_val") String src_val, @ModelAttribute("x") String x,
			@ModelAttribute("ext_type") final String ext_type, final ModelMap model, final HttpServletRequest request) throws UnsupportedOperationException, Exception {
		String final_message = null;
		JSONObject jsonObject = new JSONObject(x);
		jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String) request.getSession().getAttribute("jwt"));
		x = jsonObject.toString();
		final_message = this.es.invokeRest(x, schedularComputeUrl + "feednm/extractData");
		model.addAttribute("successString", final_message);
		/*
		 * String status0[] = resp.toString().split(":");  (status0[0]
		 * + " value " + status0[1] + " value3: " + status0[2]); String status1[] =
		 * status0[1].split(","); String status = status1[0].replaceAll("\'",
		 * "").trim(); String message0 = status0[2]; String message =
		 * message0.replaceAll("[\'}]", "").trim(); String final_message = status + ": "
		 * + message;
		 */
	
		/*
		 * if (status.equalsIgnoreCase("Failed")) { model.addAttribute("errorString",
		 * final_message); } else if (status.equalsIgnoreCase("Success")) {
		 * model.addAttribute("successString", final_message);
		 * model.addAttribute("next_button_active", "active"); }
		 */
		model.addAttribute("src_val", src_val);
		ArrayList<SourceSystemMaster> src_sys_val = this.es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
		model.addAttribute("src_sys_val", src_sys_val);
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		return new ModelAndView("extraction/ExtractData");
	}

	@RequestMapping(value = "/extraction/ViewFeedRun", method = RequestMethod.GET)
	public ModelAndView ViewFeedRun(final ModelMap model,final HttpServletRequest request) {
		ArrayList<String> feedarr;
		try {
			feedarr = this.es.getRunFeeds((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("feedarr", feedarr);
		} catch (Exception e) {

			logger.error(e.getMessage());
		}
		return new ModelAndView("extraction/FeedRun");

	}

	
	
	
	
	
	
	@RequestMapping(value = "/extraction/FeedStatus", method = RequestMethod.POST)
	public ModelAndView FeedStatus(@Valid @ModelAttribute("feed_val") final String feed_val, final ModelMap model,final HttpServletRequest request) throws IOException {
		ArrayList<RunFeedsBean> runfeeds;
		try {
			runfeeds = this.es.getLastRunFeeds((String) request.getSession().getAttribute("project_name"), feed_val);
			model.addAttribute("runfeeds", runfeeds);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return new ModelAndView("extraction/FeedRunStatus");
		
	}


	@RequestMapping(value = "/extraction/FeedDetails", method = RequestMethod.GET)
	public ModelAndView FeedDetails(final ModelMap model, HttpServletRequest request) throws IOException {
		try {
			model.addAttribute("src_val", SOURCE);
			ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val;
			
			
			src_sys_val = this.es.getSources(this.SOURCE, (String) request.getSession().getAttribute("project_name"));
			for (SourceSystemMaster ssm : src_sys_val) {
				src_sys_val1.add(ssm);
			}
			ArrayList<String> db_name = this.es.getHivedbList((String) request.getSession().getAttribute("project_name"));
			model.addAttribute("db_name", db_name);
			model.addAttribute("src_sys_val1", src_sys_val1);
       //model.addAttribute("src_sys_val1", src_sys_val2);
			model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
			model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		

		
		//return new ModelAndView("extraction/FeedDetails" + this.src_val);
		
		
		return new ModelAndView("extraction/FeedDetails" + SOURCE);
	
	}

	
	@RequestMapping(value = { "/extraction/FeedValidationDashboard" }, method = RequestMethod.POST)
	public ModelAndView FeedValidationDashboard(@Valid @ModelAttribute("src_sys_id") final int src_sys_id, @ModelAttribute("src_val") final String src_val,final ModelMap model,final HttpServletRequest request)
			throws Exception {
		String message = "Testing";
		String src_sys_val = this.es.getFeedName(src_sys_id);
		ArrayList<TempDataDetailBean> arrddb = this.es.getInProgressTempData(src_sys_id, src_val, (String) request.getSession().getAttribute("project_name"));
		
		if(arrddb.isEmpty()) {
	        	arrddb = this.es.getValidatedTempData(src_sys_id,src_val, (String) request.getSession().getAttribute("project_name"));
			if(arrddb.isEmpty()) {
	     		message = "The selected feed " + src_sys_val + " is validated and has zero errors";
				model.addAttribute("message", message);
			} else {
				
				
				
				message = "The selected feed " + src_sys_val + " validation is completed and has " + arrddb.size() + "errors";
				model.addAttribute("message", message);
			}
		} else {
			
			
			message = "The selected feed " + src_sys_val + " validation is in progress and " + arrddb.size() + " tables are yet to be validated";
			model.addAttribute("message", message);
		}
		model.addAttribute("arrddb", arrddb);

		return new ModelAndView("/extraction/FeedValidationDashboard");
	}

	
	
	
	
	
	@RequestMapping(value = { "/extraction/error" }, method = RequestMethod.GET)
	public ModelAndView error(final ModelMap modelMap,final HttpServletRequest request) {
		return new ModelAndView("/index");
	}
	
	@RequestMapping(value = { "/extraction/logout" }, method = RequestMethod.GET)
	public ModelAndView logout(final ModelMap modelMap,final HttpServletRequest request) {
		request.getSession().invalidate();
		return new ModelAndView("redirect://" + this.parentMicroServices);
	}	
	
	@RequestMapping(value = "/extraction/BulkLoadTest", method = RequestMethod.POST)
	public ModelAndView BulkLoadTest(@Valid @ModelAttribute("src_sys_id") int src_sys_id, @ModelAttribute("src_val") String src_val, @ModelAttribute("selection") String selection, ModelMap model,
			HttpServletRequest request) throws UnsupportedOperationException, Exception {
		// String href1 = es.getBulkDataTemplate(src_sys_id);
		
		String href1 = "/assets/sybase/Juniper_Extraction_Bulk_Upload_Template.xlsm";
		
		
		
		model.addAttribute("href1", href1);
				
		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
		model.addAttribute("conn_val", conn_val);
		model.addAttribute("src_sys_id", src_sys_id);
		model.addAttribute("src_val", src_val);
		model.addAttribute("selection", selection);
		return new ModelAndView("extraction/BulkLoadTest");
	}
	
	
	public File convert(MultipartFile multiPartFile) throws Exception {
		File convFile = new File(multiPartFile.getOriginalFilename());
		convFile.createNewFile();
		FileOutputStream fos = new FileOutputStream(convFile);
		fos.write(multiPartFile.getBytes());
		fos.close();
		return convFile;

	}

	@RequestMapping(value = "/extraction/CreateBulkLoadDetails", method = RequestMethod.POST)
	public ModelAndView CreateBulkLoadDetails(@Valid @ModelAttribute("src_val") String src_val, @Valid @ModelAttribute("feed_id") int src_sys_id, @RequestParam("file") MultipartFile multiPartFile1,
			ModelMap model, HttpServletRequest request, Principal principal) throws UnsupportedOperationException, Exception {

		
		
		String resp = null;
		
		String usernm = (String) request.getSession().getAttribute("user_name");
		String project = (String) request.getSession().getAttribute("project_name");
		File file = convert(multiPartFile1);
		String str = es.getJsonFromFile(file, usernm, project, src_sys_id);
		String json_array_metadata_str = es.getJsonFromFeedSequence(project, Integer.toString(src_sys_id));
		/*
		 * String json_array_str=es.getJsonFromFile(file,usernm,project,src_sys_id);
		 * JSONObject jsonObject= new JSONObject(json_array_str);
		 * jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String)
		 * request.getSession().getAttribute("jwt"));
		 * json_array_str=jsonObject.toString();
		 */
		resp = es.invokeRest(str, sybaseBackendUrl + "addTempTableInfo");
		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			resp = es.invokeRest(json_array_metadata_str, sybaseBackendUrl + "metaDataValidation");
			String statusNew0[] = resp.toString().split(":");
			String statusNew1[] = statusNew0[1].split(",");
			status = statusNew1[0].replaceAll("\'", "").trim();
			message0 = statusNew0[2];
			message = message0.replaceAll("[\'}]", "").trim();
			final_message = status + ": " + message;
			if (status.equalsIgnoreCase("Success")) {
				model.addAttribute("successString", final_message);
			} else if (status.equalsIgnoreCase("Failed")) {
				model.addAttribute("errorString", final_message);
			}
		}
		// model.addAttribute("successString", resp);
		ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
		ArrayList<SourceSystemMaster> src_sys_val2 = new ArrayList<SourceSystemMaster>();
		ArrayList<SourceSystemMaster> src_sys_val = es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
		for (SourceSystemMaster ssm : src_sys_val) {
			if ((ssm.getFile_list() == null || ssm.getFile_list().isEmpty()) && (ssm.getTable_list() == null || ssm.getTable_list().isEmpty())
					&& (ssm.getDb_name()==null || ssm.getFile_list().isEmpty())) // 3rd Added for Hive 
			{
				src_sys_val1.add(ssm);
			} else {
				src_sys_val2.add(ssm);
			}
		}
		ArrayList<String> db_name = es.getHivedbList((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("db_name", db_name);
		model.addAttribute("src_sys_val", src_sys_val);
		model.addAttribute("src_sys_val1", src_sys_val1);
		model.addAttribute("src_sys_val2", src_sys_val2);
		model.addAttribute("usernm", usernm);
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		return new ModelAndView("extraction/DataDetails" + src_val);
	}

	@RequestMapping("/{fileName:.+}")
	public void downloadfile(HttpServletRequest request, HttpServletResponse response, @PathVariable("fileName") String fileName) throws IOException {
		//  ("Ye hain file name :"+fileName);
		File file = new File(fileName);
		if (file.exists()) {

			// get the mimetype
			String mimeType = URLConnection.guessContentTypeFromName(file.getName());
			if (mimeType == null) {
				// unknown mimetype so set the mimetype to application/octet-stream
				mimeType = "application/octet-stream";
			}

			response.setContentType(mimeType);

			/**
			 * In a regular HTTP response, the Content-Disposition response header is a
			 * header indicating if the content is expected to be displayed inline in the
			 * browser, that is, as a Web page or as part of a Web page, or as an
			 * attachment, that is downloaded and saved locally.
			 * 
			 */

			/**
			 * Here we have mentioned it to show inline
			 */
			// response.setHeader("Content-Disposition", String.format("inline; filename=\""
			// + file.getName() + "\""));

			// Here we have mentioned it to show as attachment
			
			
			
			
			response.setHeader("Content-Disposition", String.format("attachment; filename=\"" + file.getName() + "\""));

			response.setContentLength((int) file.length());

			InputStream inputStream = new BufferedInputStream(new FileInputStream(file));

			FileCopyUtils.copy(inputStream, response.getOutputStream());
		}

	}

	@RequestMapping(value = "/extraction/DataDetailsSybase3", method = RequestMethod.POST)
	public ModelAndView DataDetails3(@Valid @ModelAttribute("src_val") String src_val, @ModelAttribute("x") String x, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
		String resp = "";
		String src_sys_id = "";
		String project = (String) request.getSession().getAttribute("project_name");
		
	
		
		JSONObject jsonObject = new JSONObject(x);
		jsonObject.getJSONObject("body").getJSONObject("data").put("jwt", (String) request.getSession().getAttribute("jwt"));
		x = jsonObject.toString();

		if (x.contains("feed_id1")) {
			x = x.replace("feed_id1", "feed_id");

			resp = es.invokeRest(x, sybaseBackendUrl + "editTempTableInfo");
		} else {
			resp = es.invokeRest(x, sybaseBackendUrl + "addTempTableInfo");
		}

		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		} else if (status.equalsIgnoreCase("Success")) {
			JSONObject jsonObject1 = new JSONObject(x);
			src_sys_id = jsonObject1.getJSONObject("body").getJSONObject("data").getString("feed_id");
			String json_array_metadata_str = es.getJsonFromFeedSequence(project, src_sys_id);
			
			
			
			
			resp = es.invokeRestAsyncronous(json_array_metadata_str, sybaseBackendUrl + "metaDataValidation");
			
			if (resp.equalsIgnoreCase("Started")) {
				model.addAttribute("successString", "Metadata validation started");
			} 
		}
		ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
		ArrayList<SourceSystemMaster> src_sys_val2 = new ArrayList<SourceSystemMaster>();
		ArrayList<SourceSystemMaster> src_sys_val = es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
		for (SourceSystemMaster ssm : src_sys_val) {
			if ((ssm.getFile_list() == null || ssm.getFile_list().isEmpty()) && (ssm.getTable_list() == null || ssm.getTable_list().isEmpty())
					&& (ssm.getDb_name()==null || ssm.getDb_name().isEmpty())) // 3rd Added for Hive 
				{
				src_sys_val1.add(ssm);
			} else {
				src_sys_val2.add(ssm);
			}
		}
		model.addAttribute("src_sys_val1", src_sys_val1);
		model.addAttribute("src_sys_val2", src_sys_val2);
		ArrayList<String> db_name = es.getHivedbList((String) request.getSession().getAttribute("project_name"));
		model.addAttribute("db_name", db_name);
		model.addAttribute("usernm", request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		return new ModelAndView("extraction/DataDetails" + src_val);
	}

	@RequestMapping(value = "/extraction/DataDetailsEditSybase1", method = RequestMethod.POST)
	public ModelAndView DataDetailsEdit1(@Valid @ModelAttribute("src_sys_id") int src_sys_id, @ModelAttribute("src_val") String src_val, ModelMap model, HttpServletRequest request)
			throws UnsupportedOperationException, Exception {
//		  String href1=es.getBulkDataTemplate(src_sys_id); 
		String href1= "Juniper_Extraction_bulk_Upload_Template.xlsm"; 
		model.addAttribute("href1", href1);
		String db_name = null;
		ConnectionMaster conn_val = es.getConnections1(src_val, src_sys_id);
		model.addAttribute("conn_val", conn_val);
		String ext_type = es.getExtType(src_sys_id);
		model.addAttribute("ext_type", ext_type);
		ArrayList<String> schema_name = es.getSchema(src_val, conn_val.getConnection_id(), (String) request.getSession().getAttribute("project_name"), db_name);
		model.addAttribute("schema_name", schema_name);
		ArrayList<DataDetailBean> arrddb = es.getData(src_sys_id, src_val, conn_val.getConnection_id(), (String) request.getSession().getAttribute("project_name"), db_name);
		model.addAttribute("arrddb", arrddb);
		model.addAttribute("counter_val", arrddb.size());
		model.addAttribute("usernm", (String) request.getSession().getAttribute("user_name"));
		model.addAttribute("project", (String) request.getSession().getAttribute("project_name"));
		return new ModelAndView("extraction/DataDetailsEditSybase1");
	}

	
	
	
	
	
	
	@RequestMapping(value = "/extraction/EditBulkLoadDetails", method = RequestMethod.POST)
	public ModelAndView EditBulkLoadDetails(@Valid @ModelAttribute("src_val") String src_val, @Valid @ModelAttribute("feed_id1") String src_sys_id, @RequestParam("file") MultipartFile multiPartFile1,
			ModelMap model, HttpServletRequest request, Principal principal) throws UnsupportedOperationException, Exception {
		String resp = null;
		String usernm = (String) request.getSession().getAttribute("user_name");
		String project = (String) request.getSession().getAttribute("project_name");
		File file = convert(multiPartFile1);
		String json_array_str = es.getJsonFromFile(file, usernm, project, Integer.parseInt(src_sys_id));
		String json_array_metadata_str = es.getJsonFromFeedSequence(project, src_sys_id);
		resp = es.invokeRest(json_array_str, sybaseBackendUrl + "editTempTableInfo");
		String status0[] = resp.toString().split(":");
		String status1[] = status0[1].split(",");
		String status = status1[0].replaceAll("\'", "").trim();
		String message0 = status0[2];
		String message = message0.replaceAll("[\'}]", "").trim();
		String final_message = status + ": " + message;
		if (status.equalsIgnoreCase("Failed")) {
			model.addAttribute("errorString", final_message);
		}
		else if (status.equalsIgnoreCase("Success")) {
	
			resp = es.invokeRestAsyncronous(json_array_metadata_str, sybaseBackendUrl + "metaDataValidation");
			if (resp.equalsIgnoreCase("Started")) {
				model.addAttribute("SuccessString", "Metadata validation started");
			}
		}	
						
			ArrayList<SourceSystemMaster> src_sys_val1 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val2 = new ArrayList<SourceSystemMaster>();
			ArrayList<SourceSystemMaster> src_sys_val = es.getSources(src_val, (String) request.getSession().getAttribute("project_name"));
			
			for (SourceSystemMaster ssm : src_sys_val) {
				if((ssm.getFile_list() == null || ssm.getFile_list().isEmpty())
						&& (ssm.getTable_list() == null || ssm.getTable_list().isEmpty())
						&& (ssm.getDb_name() == null || ssm.getDb_name().isEmpty())) {
					src_sys_val1.add(ssm);
				} else {
					src_sys_val2.add(ssm);
				}
					
			}
			ArrayList<String> db_name = es.getHivedbList(project);
			model.addAttribute("db_name", db_name);
			model.addAttribute("src_sys_val", src_sys_val);
			model.addAttribute("src_sys_val1", src_sys_val1);
			model.addAttribute("src_sys_val2", src_sys_val2);
			model.addAttribute("usernm", usernm);
			model.addAttribute("project", project);
			return new ModelAndView("extraction/DataDetails" + src_val) ;
		}
			
}
