package au.com.cfs.winged.servlets;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.Replicator;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Servlet.class, immediate = true, property = {"sling.servlet.paths=/bin/cfs/deactivate-pages", "sling.servlet.methods=GET"})
public class BulkPageDeactivationServlet extends SlingSafeMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkPageDeactivationServlet.class);

    @Reference
    Replicator replicator;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        PrintWriter printWriter = null;
        try {
        printWriter = response.getWriter();
        ResourceResolver resolver = request.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        String damPath = request.getParameter("damPath");
        String dryRun = request.getParameter("dryRun");
        Resource excelResource = resolver.getResource(damPath);

        List<String> childPagesList = new ArrayList<String>();
        List<String> leafPagesList = new ArrayList<String>();
        List<String> unavailablePagesList = new ArrayList<String>();

        if (excelResource != null) {

                Asset asset = excelResource.adaptTo(Asset.class);

                InputStream inputStream = null;

                if (asset != null) {
                    Rendition original = asset.getOriginal();

                    if (original != null) {
                        inputStream = original.getStream();
                    }
                }


                Workbook workbook = new XSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);

                Iterator<Row> rowIterator = sheet.iterator();

                int index = 0;

                printWriter.write("\nTotal list of pages\n=======================\n");
                while(rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    Iterator<Cell> cellIterator = row.cellIterator();

                    while(cellIterator.hasNext()) {
                        index++;
                        Cell cell = cellIterator.next();
                        String pagePath = cell.toString();
                        pagePath = pagePath.trim();

                        Resource pageResource = resolver.getResource(pagePath);

                        if(pageResource != null) {

                            printWriter.write(index + "." + pagePath + "\n");
                            boolean hasChildPages = hasChildPages(pageResource);

                            if(StringUtils.isNotBlank(dryRun) && "false".equalsIgnoreCase(dryRun)) {
                                replicator.replicate(session, ReplicationActionType.DEACTIVATE, pagePath);
                            }

                            if(!hasChildPages) {
                                leafPagesList.add(pagePath);
                            } else {
                                childPagesList.add(pagePath);
                            }

                        } else {
                            unavailablePagesList.add(pagePath);
                        }
                    }
                }


                printWriter.write("\n\nPages that don't have any child pages\n=======================\n");
                for(int i=0;i<leafPagesList.size(); i++) {
                    printWriter.write((i+1) + "." + leafPagesList.get(i) + "\n");
                }

                printWriter.write("\n\nPages that has child pages\n=======================\n");
                for(int i=0;i<childPagesList.size(); i++) {
                    printWriter.write((i+1) + "." + childPagesList.get(i) + "\n");
                }

                if(unavailablePagesList.size() > 0) {
                    printWriter.write("\n\nPages that are not available\n=======================\n");
                    for (int i = 0; i < unavailablePagesList.size(); i++) {
                        printWriter.write((i + 1) + "." + unavailablePagesList.get(i) + "\n");
                    }
                }

                if(StringUtils.isNotBlank(dryRun) && "false".equalsIgnoreCase(dryRun)) {
                    printWriter.write("\nAll the pages along with child pages have been deactivated as the dryRun is set to false.\n");
                } else if("true".equalsIgnoreCase(dryRun)) {
                    printWriter.write("\nThe changes have not been saved as the dryRun is set to true. Please try again with changing dryRun parameter as false to save the changes.\n");
                }
                workbook.close();
            } else {
            printWriter.write("Excel file not found");
            }
        } catch(IOException e) {
            printWriter.write("Error reading Excel: "+ e.getMessage());
            e.printStackTrace();
        } catch(Exception e) {
            printWriter.write("Error reading Excel: "+ e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean hasChildPages(Resource pageResource) {
        Iterator<Resource> childResources = pageResource.listChildren();
        int index=0;
        boolean hasChildPage = false;
        while(childResources.hasNext()) {
            if(index>1) {
                hasChildPage = true;
                break;
            }
            childResources.next();
            index++;

        }

        return hasChildPage;
    }

}
