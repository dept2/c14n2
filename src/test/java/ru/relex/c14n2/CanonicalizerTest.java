package ru.relex.c14n2;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;


public class CanonicalizerTest{

	@Test
	public void testN1Default() {
		Assert.assertTrue(processTest("inC14N1", "c14nDefault"));
	}

	@Test
	public void testN1Comment() {
		Assert.assertTrue(processTest("inC14N1", "c14nComment"));
	}

	@Test
	public void testN2Default() {
		Assert.assertTrue(processTest("inC14N2", "c14nDefault"));
	}

	@Test
	public void testN2Trim() {
		Assert.assertTrue(processTest("inC14N2", "c14nTrim"));
	}

	@Test
	public void testN21Default() {
		Assert.assertTrue(processTest("inC14N2_1", "c14nDefault"));
	}

	@Test
	public void testN21Trim() {
		Assert.assertTrue(processTest("inC14N2_1", "c14nTrim"));
	}

	@Test
	public void testN3Default() {
		Assert.assertTrue(processTest("inC14N3", "c14nDefault"));
	}

	@Test
	public void testN3Prefix() {
		Assert.assertTrue(processTest("inC14N3", "c14nPrefix"));
	}

	@Test
	public void testN3Trim() {
		Assert.assertTrue(processTest("inC14N3", "c14nTrim"));
	}

	@Test
	public void testN4Default() {
		Assert.assertTrue(processTest("inC14N4", "c14nDefault"));
	}

	@Test
	public void testN4Trim() {
		Assert.assertTrue(processTest("inC14N4", "c14nTrim"));
	}

	@Test
	public void testN5Default() {
		Assert.assertTrue(processTest("inC14N5", "c14nDefault"));
	}

	@Test
	public void testN5Trim() {
		Assert.assertTrue(processTest("inC14N5", "c14nTrim"));
	}

	@Test
	public void testN6Default() {
		Assert.assertTrue(processTest("inC14N6", "c14nDefault"));
	}

	@Test
	public void testNsPushdownDefault() {
		Assert.assertTrue(processTest("inNsPushdown", "c14nDefault"));
	}

	@Test
	public void testNsPushdownPrefix() {
		Assert.assertTrue(processTest("inNsPushdown", "c14nPrefix"));
	}

	@Test
	public void testNsDefaultDefault() {
		Assert.assertTrue(processTest("inNsDefault", "c14nDefault"));
	}

	@Test
	public void testNsDefaultPrefix() {
		Assert.assertTrue(processTest("inNsDefault", "c14nPrefix"));
	}

	@Test
	public void testNsSortDefault() {
		Assert.assertTrue(processTest("inNsSort", "c14nDefault"));
	}

	@Test
	public void testNsSortPrefix() {
		Assert.assertTrue(processTest("inNsSort", "c14nPrefix"));
	}

	@Test
	public void testNsRedeclDefault() {
		Assert.assertTrue(processTest("inNsRedecl", "c14nDefault"));
	}

	@Test
	public void testNsRedeclPrefix() {
		Assert.assertTrue(processTest("inNsRedecl", "c14nPrefix"));
	}

	@Test
	public void testNsSuperfluousDefault() {
		Assert.assertTrue(processTest("inNsSuperfluous", "c14nDefault"));
	}

	@Test
	public void testNsSuperfluousPrefix() {
		Assert.assertTrue(processTest("inNsSuperfluous", "c14nPrefix"));
	}

	@Test
	public void testNsXmlDefault() {
		Assert.assertTrue(processTest("inNsXml", "c14nDefault"));
	}

	@Test
	public void testNsXmlPrefix() {
		Assert.assertTrue(processTest("inNsXml", "c14nPrefix"));
	}

	@Test
	public void testNsXmlQname() {
		Assert.assertTrue(processTest("inNsXml", "c14nQname"));
	}

	@Test
	public void testNsXmlPrefixQname() {
		Assert.assertTrue(processTest("inNsXml", "c14nPrefixQname"));
	}

	@Test
	public void testNsContentDefault() {
		Assert.assertTrue(processTest("inNsContent", "c14nDefault"));
	}

	@Test
	public void testNsContentQnameElem() {
		Assert.assertTrue(processTest("inNsContent", "c14nQnameElem"));
	}

	@Test
	public void testNsContentQnameXpathElem() {
		Assert.assertTrue(processTest("inNsContent", "c14nQnameXpathElem"));
	}

	@Test
	public void testNsContentPrefixQnameXPathElem() {
		Assert.assertTrue(processTest("inNsContent", "c14nPrefixQnameXPathElem"));
	}

	@Test
	public void testRC242Default() {
		Assert.assertTrue(processTest("inRC2_4_2", "c14nDefault"));
	}

	private static boolean processTest(String inFileName, String paramName) {
		try {
			long l = System.currentTimeMillis();
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			String path = CanonicalizerTest.class.getProtectionDomain()
					.getCodeSource().getLocation().getPath();

			Document doc = dBuilder.parse(new FileInputStream(path + inFileName
					+ ".xml"));
			DOMCanonicalizer rf = new DOMCanonicalizer(doc,
					getParams(paramName));
			String result = rf.canonicalize();
			System.out.println("l = " + (System.currentTimeMillis() - l)
					/ 1000.0 + "s");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			FileInputStream fis = new FileInputStream(path + "out_"
					+ inFileName + "_" + paramName + ".xml");
			byte[] bytes = new byte[1024];
			int cnt = 0;
			while ((cnt = fis.read(bytes)) > -1)
				baos.write(bytes, 0, cnt);
			fis.close();
			baos.flush();
			baos.close();
			for (int i = 0; i < result.length(); i++)
				if (result.charAt(i) != baos.toString("UTF-8").charAt(i)) {
					i = 0;
					break;
				}
			System.out.println(baos.toString("UTF-8") + " "
					+ result.equals(baos.toString("UTF-8")));
			return result.equals(baos.toString("UTF-8"));
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	private static Parameters getParams(String paramName) {
		Parameters params = new Parameters();
		if ("c14nDefault".equals(paramName)) {
		} else if ("c14nComment".equals(paramName)) {
			params.setIgnoreComments(false);
		} else if ("c14nTrim".equals(paramName)) {
			params.setTrimTextNodes(true);
		} else if ("c14nPrefix".equals(paramName)) {
			params.setPrefixRewrite(Parameters.SEQUENTIAL);
		} else if ("c14nQname".equals(paramName)) {
			params.getQnameAwareAttributes().add(
					new QNameAwareParameter("type",
							"http://www.w3.org/2001/XMLSchema-instance"));
		} else if ("c14nPrefixQname".equals(paramName)) {
			params.setPrefixRewrite(Parameters.SEQUENTIAL);
			params.getQnameAwareAttributes().add(
					new QNameAwareParameter("type",
							"http://www.w3.org/2001/XMLSchema-instance"));
		} else if ("c14nQnameElem".equals(paramName)) {
			params.getQnameAwareElements().add(
					new QNameAwareParameter("bar", "http://a"));
		} else if ("c14nQnameXpathElem".equals(paramName)) {
			params.getQnameAwareElements().add(
					new QNameAwareParameter("bar", "http://a"));
			params.getQnameAwareXPathElements().add(
					new QNameAwareParameter("IncludedXPath",
							"http://www.w3.org/2010/xmldsig2#"));
		} else if ("c14nPrefixQnameXPathElem".equals(paramName)) {
			params.setPrefixRewrite(Parameters.SEQUENTIAL);
			params.getQnameAwareElements().add(
					new QNameAwareParameter("bar", "http://a"));
			params.getQnameAwareXPathElements().add(
					new QNameAwareParameter("IncludedXPath",
							"http://www.w3.org/2010/xmldsig2#"));
		}
		return params;
	}
}