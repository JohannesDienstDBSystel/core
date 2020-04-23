package com.dotcms.browser;

import static com.dotmarketing.business.PermissionAPI.PERMISSION_READ;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.dotcms.business.CloseDBIfOpened;
import com.dotcms.contenttype.exception.NotFoundInDbException;
import com.dotcms.contenttype.model.type.BaseContentType;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.Role;
import com.dotmarketing.business.web.UserWebAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.comparators.WebAssetMapComparator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.transform.ContentletToMapTransformer;
import com.dotmarketing.portlets.fileassets.business.FileAsset;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.htmlpageasset.model.HTMLPageAsset;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.links.model.Link;
import com.dotmarketing.portlets.workflows.business.WorkflowAPI;
import com.dotmarketing.portlets.workflows.model.WorkflowAction;
import com.dotmarketing.portlets.workflows.model.WorkflowScheme;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilHTML;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.language.LanguageUtil;
import com.liferay.portal.model.User;
import io.vavr.control.Try;

public class BrowserAPI {

    private LanguageAPI langAPI = APILocator.getLanguageAPI();
    private UserWebAPI userAPI = WebAPILocator.getUserWebAPI();
    private FolderAPI folderAPI = APILocator.getFolderAPI();
    private PermissionAPI permissionAPI = APILocator.getPermissionAPI();
    
    @Deprecated
    public Map<String, Object> getFolderContent ( User usr, String folderId, int offset, int maxResults, String filter, List<String> mimeTypes,
    		List<String> extensions, boolean showArchived, boolean noFolders, boolean onlyFiles, String sortBy, boolean sortByDesc, boolean excludeLinks, long languageId ) throws DotSecurityException, DotDataException {
    	return getFolderContent(usr, folderId, offset, maxResults, filter, mimeTypes, extensions, true, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, excludeLinks, languageId);
    }
    @Deprecated
    public Map<String, Object> getFolderContent ( User usr, String folderId, int offset, int maxResults, String filter, List<String> mimeTypes,
                                                  List<String> extensions, boolean showArchived, boolean noFolders, boolean onlyFiles, String sortBy, boolean sortByDesc, long languageId ) throws DotSecurityException, DotDataException {
    	return getFolderContent(usr, folderId, offset, maxResults, filter, mimeTypes, extensions, true, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, languageId);
    }
    @Deprecated
    public Map<String, Object> getFolderContent(User user, String folderId,
			int offset, int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showWorking, boolean showArchived, boolean noFolders,
			boolean onlyFiles, String sortBy, boolean sortByDesc)
			throws DotSecurityException, DotDataException {

    	return getFolderContent(user, folderId, offset, maxResults, filter, mimeTypes, extensions, showWorking, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, false, 0);

    }
    @Deprecated
    public Map<String, Object> getFolderContent(User user, String folderId,
			int offset, int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showWorking, boolean showArchived, boolean noFolders,
			boolean onlyFiles, String sortBy, boolean sortByDesc, long languageId)
			throws DotSecurityException, DotDataException {

    	return getFolderContent(user, folderId, offset, maxResults, filter, mimeTypes, extensions, showWorking, showArchived, noFolders, onlyFiles, sortBy, sortByDesc, false, languageId);

    }
    
    protected class WfData {
        
        List<WorkflowAction> wfActions = new ArrayList<WorkflowAction>();
        boolean contentEditable = false;
        List<Map<String, Object>> wfActionMapList = new ArrayList<Map<String, Object>>();
        
        boolean skip=false;
        
        public WfData(Contentlet contentlet, List<Integer> permissions, User user, boolean showArchived) throws DotStateException, DotDataException, DotSecurityException {
            
            
            if(null==contentlet) {
                return;
            }

            wfActions = APILocator.getWorkflowAPI().findAvailableActions(contentlet, user, WorkflowAPI.RenderMode.LISTING);
            
            if (permissionAPI.doesUserHavePermission(contentlet, PermissionAPI.PERMISSION_WRITE, user) && contentlet.isLocked()) {
                String lockedUserId = APILocator.getVersionableAPI()
                        .getLockedBy(contentlet);
                if (user.getUserId().equals(lockedUserId)) {
                    contentEditable = true;
                } else {
                    contentEditable = false;
                }
            } else {
                contentEditable = false;
            }
            



            if (permissions.contains(PERMISSION_READ)) {
                if (!showArchived && contentlet.isArchived()) {
                    skip=true;
                    return;
                }
                final boolean showScheme = (wfActions!=null) ?  wfActions.stream().collect(Collectors.groupingBy(WorkflowAction::getSchemeId)).size()>1 : false;
                
                for (final WorkflowAction action : wfActions) {

					WorkflowScheme wfScheme = APILocator.getWorkflowAPI().findScheme(action.getSchemeId());
                    Map<String, Object> wfActionMap = new HashMap<String, Object>();
                    wfActionMap.put("name", action.getName());
                    wfActionMap.put("id", action.getId());
                    wfActionMap.put("icon", action.getIcon());
                    wfActionMap.put("assignable", action.isAssignable());
                    wfActionMap.put("commentable", action.isCommentable()
                            || UtilMethods.isSet(action.getCondition()));

                    final String actionName = Try.of(() ->LanguageUtil.get(user, action.getName())).getOrElse(action.getName());
                    final String schemeName = Try.of(() ->LanguageUtil.get(user,wfScheme.getName())).getOrElse(wfScheme.getName());
                    
                    final String actionNameStr = (showScheme) ? actionName +" ( "+schemeName+" )" : actionName;

                    wfActionMap.put("wfActionNameStr",actionNameStr);
                    wfActionMap.put("hasPushPublishActionlet", action.hasPushPublishActionlet());
                    wfActionMapList.add(wfActionMap);
                }

            }


        }
    }
    @Deprecated
    public Map<String, Object> getFolderContent(User user, String folderId,
                    int offset, int maxResults, String filter, List<String> mimeTypes,
                    List<String> extensions, boolean showWorking, boolean showArchived, boolean noFolders,
                    boolean onlyFiles, String sortBy, boolean sortByDesc, boolean excludeLinks, long languageId)
                    throws DotSecurityException, DotDataException {
        
        
        return getFolderContent( user,  folderId,
                         offset,  maxResults,  filter,  mimeTypes,
                        extensions,  showWorking,  showArchived,  noFolders,
                         onlyFiles,  sortBy,  sortByDesc,  excludeLinks,  languageId, false);
        
    }
    
	/**
	 * Gets the Folders, HTMLPages, Links, FileAssets under the specified folderId.
	 *
	 * @param user
	 * @param folderId
	 * @param offset
	 * @param maxResults
	 * @param filter
	 * @param mimeTypes
	 * @param extensions
	 *
	 * @param showWorking   If true, returns the working version of HTMLPages, Links and FileAssets in the folder.
	 * 						If false, returns the live version of HTMLPages, Links and FileAssets in the folder.
	 *
	 * @param showArchived  If true, includes the archived version of HTMLPages, Links and FileAssets in the folder.
	 *
	 * @param noFolders
	 * @param onlyFiles
	 * @param sortBy
	 * @param sortByDesc
	 * @param excludeLinks
	 * @return
	 * @throws DotSecurityException
	 * @throws DotDataException
	 */
    @Deprecated
	public Map<String, Object> getFolderContent(User user, String folderId,
			int offset, int maxResults, String filter, List<String> mimeTypes,
			List<String> extensions, boolean showWorking, boolean showArchived, boolean noFolders,
			boolean onlyFiles, String sortBy, boolean sortByDesc, boolean excludeLinks, long languageId, boolean dotAssets)
			throws DotSecurityException, DotDataException {

	    final boolean showPages=!onlyFiles;

	    BrowserQuery browserQuery = BrowserQuery.builder()
	                    .showDotAssets(dotAssets)
	                    .showLinks(!excludeLinks)
	                    .showExtensions(extensions)
	                    .withFilter(filter)
	                    .withHostOrFolderId(folderId)
	                    .withLanguageId(languageId)
	                    .offset(offset)
	                    .showFiles(true)
	                    .showPages(showPages)
	                    .showFolders(!noFolders)
	                    .showArchived(showArchived)
	                    .showWorking(showWorking)
	                    .sortBy(sortBy)
	                    .sortByDesc(sortByDesc)
	                    .withUser(user).build();
	    
	    return getFolderContent(browserQuery);

	}
	
    @CloseDBIfOpened
    public Map<String, Object> getFolderContent(final BrowserQuery browserQuery) throws DotSecurityException, DotDataException {


        List<Map<String, Object>> returnList = new ArrayList<>();

        Role[] roles = APILocator.getRoleAPI().loadRolesForUser(browserQuery.user.getUserId())
                        .toArray(new Role[0]);


        // gets folder parent
        Folder parent = folderAPI.find(browserQuery.hostFolderId, browserQuery.user, false);


        Host host = null;
        if (parent == null) {// If we didn't find a parent folder lets verify if
                             // this is a host
            host = APILocator.getHostAPI().find(browserQuery.hostFolderId, browserQuery.user, false);

            if (host == null) {
                Logger.error(this, "Folder ID doesn't belong to a Folder nor a Host, id: " + browserQuery.hostFolderId
                                + ", maybe the Folder was modified in the background.");
                throw new NotFoundInDbException("Folder ID doesn't belong to a Folder nor a Host, id: " + browserQuery.hostFolderId);
            }
        }

        if (browserQuery.showFolders) {
            if (parent != null) {
                List<Folder> folders = new ArrayList<Folder>();
                try {
                    folders = folderAPI.findSubFolders(parent, userAPI.getSystemUser(), false);
                } catch (Exception e1) {
                    Logger.error(this, "Could not load folders : ", e1);
                }
                for (Folder folder : folders) {
                    List<Integer> permissions = permissionAPI.getPermissionIdsFromRoles(folder, roles, browserQuery.user);
                    if (permissions.contains(PERMISSION_READ)) {
                        Map<String, Object> folderMap = folder.getMap();
                        folderMap.put("permissions", permissions);
                        folderMap.put("parent", folder.getInode());
                        folderMap.put("mimeType", "");
                        folderMap.put("name", folder.getName());
                        folderMap.put("title", folder.getName());
                        folderMap.put("description", folder.getTitle());
                        folderMap.put("extension", "folder");
                        folderMap.put("hasTitleImage", "");
                        folderMap.put("__icon__", "folderIcon");
                        returnList.add(folderMap);
                    }
                }
            }
        }

        if (browserQuery.showLinks) {

            // Getting the links directly under the parent folder or host
            List<Link> links = new ArrayList<Link>();

            if (parent != null) {
                if (browserQuery.showWorking) {
                    links.addAll(folderAPI.getLinks(parent, true, false, browserQuery.user, false));
                } else {
                    links.addAll(folderAPI.getLiveLinks(parent, browserQuery.user, false));
                }
                if (browserQuery.showArchived)
                    links.addAll(folderAPI.getLinks(parent, true, browserQuery.showArchived, browserQuery.user, false));
            } else {
                links = folderAPI.getLinks(host, true, browserQuery.showArchived, browserQuery.user, false);
            }


            for (Link link : links) {

                List<Integer> permissions2 = permissionAPI.getPermissionIdsFromRoles(link, roles, browserQuery.user);

                if (permissions2.contains(PERMISSION_READ)) {
                    Map<String, Object> linkMap = link.getMap();
                    linkMap.put("permissions", permissions2);
                    linkMap.put("mimeType", "application/dotlink");
                    linkMap.put("name", link.getTitle());
                    linkMap.put("title", link.getName());
                    linkMap.put("description", link.getFriendlyName());
                    linkMap.put("extension", "link");
                    linkMap.put("hasLiveVersion", APILocator.getVersionableAPI().hasLiveVersion(link));
                    linkMap.put("statusIcons", UtilHTML.getStatusIcons(link));
                    linkMap.put("hasTitleImage", "");
                    linkMap.put("__icon__", "linkIcon");
                    returnList.add(linkMap);
                }
            }


        }

        StringBuilder luceneQuery = new StringBuilder();

        List<String> baseTypes = new ArrayList<>();
        if(browserQuery.showDotAssets) {
            baseTypes.add("9");
        }
        if(browserQuery.showFiles) {
            baseTypes.add("4");
        }
        if(browserQuery.showPages) {
            baseTypes.add("5");
        }
        if(baseTypes.size()==0) {
            baseTypes.add("0");
        }
        
        luceneQuery.append("+basetype:(" + String.join(" OR ", baseTypes) + ") ");


        luceneQuery.append((browserQuery.languageId > 0) ? " +languageid:" + browserQuery.languageId : "");
        luceneQuery.append((host != null) ? " +conhost:" + host.getIdentifier() + " +confolder:" + Folder.SYSTEM_FOLDER : "");
        luceneQuery.append((parent != null) ? " +confolder:" + parent.getInode() : "");


        if (UtilMethods.isSet(browserQuery.filter)) {
            String[] spliter = browserQuery.filter.split(" ");
            for (String tok : spliter) {
                luceneQuery.append(" +title:" + tok + "*");
            }
        }

        luceneQuery.append(
                    browserQuery.showArchived 
                        ? " +(working:true OR deleted:true) "
                        : browserQuery.showWorking 
                            ? " +working:true -deleted:true" 
                            : " +live:true");


        final String esSortBy = ("name".equals(browserQuery.sortBy) ? "title" : browserQuery.sortBy) + (browserQuery.sortByDesc ? " desc" : "");

        List<Contentlet> contentlets = APILocator.getContentletAPI().search(luceneQuery.toString(), browserQuery.maxResults,
                        browserQuery.offset, esSortBy, browserQuery.user, true);



        for (Contentlet contentlet : contentlets) {
            Map<String, Object> contentMap = null;
            if (contentlet.getBaseType().get() == BaseContentType.FILEASSET) {
                FileAsset fileAsset = APILocator.getFileAssetAPI().fromContentlet(contentlet);
                contentMap = fileAssetMap(fileAsset, browserQuery.user, browserQuery.showArchived);
            }

            if (contentlet.getBaseType().get() == BaseContentType.DOTASSET) {
                contentMap = dotAssetMap(contentlet, browserQuery.user, browserQuery.showArchived);
            }

            if (contentlet.getBaseType().get() == BaseContentType.HTMLPAGE) {
                HTMLPageAsset page = APILocator.getHTMLPageAssetAPI().fromContentlet(contentlet);
                contentMap = htmlPageMap(page, browserQuery.user, browserQuery.showArchived, browserQuery.languageId);
            }
            
            List<Integer> permissions = permissionAPI.getPermissionIdsFromRoles(contentlet, roles, browserQuery.user);
            WfData wfdata = new WfData(contentlet, permissions, browserQuery.user, browserQuery.showArchived);
            contentMap.put("wfActionMapList", wfdata.wfActionMapList);
            contentMap.put("contentEditable", wfdata.contentEditable);
            contentMap.put("permissions", permissions);
            returnList.add(contentMap);
        }



        // Filtering
        List<Map<String, Object>> filteredList = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> asset : returnList) {

            String name = (String) asset.get("name");
            name = name == null ? "" : name;
            String description = (String) asset.get("description");
            description = description == null ? "" : description;
            String mimeType = (String) asset.get("mimeType");
            mimeType = mimeType == null ? "" : mimeType;


            if (browserQuery.mimeTypes != null && browserQuery.mimeTypes.size() > 0) {
                boolean match = false;
                for (String mType : browserQuery.mimeTypes)
                    if (mimeType.contains(mType))
                        match = true;
                if (!match)
                    continue;
            }
            if (browserQuery.extensions != null && browserQuery.extensions.size() > 0) {
                boolean match = false;
                for (String ext : browserQuery.extensions)
                    if (((String) asset.get("extension")).contains(ext))
                        match = true;
                if (!match)
                    continue;
            }
            filteredList.add(asset);
        }
        returnList = filteredList;

        // Sorting
        WebAssetMapComparator comparator = new WebAssetMapComparator(browserQuery.sortBy, browserQuery.sortByDesc);
        Collections.sort(returnList, comparator);

        int offset = browserQuery.offset;
        int maxResults = browserQuery.maxResults;
        // Offsetting
        if (offset < 0)
            offset = 0;
        if (maxResults <= 0)
            maxResults = returnList.size() - offset;
        if (maxResults + offset > returnList.size())
            maxResults = returnList.size() - offset;

        Map<String, Object> returnMap = new HashMap<String, Object>();
        returnMap.put("total", returnList.size());
        returnMap.put("list", returnList.subList(offset, offset + maxResults));
        return returnMap;
    }

	
	   private Map<String,Object> htmlPageMap(HTMLPageAsset page, User user, boolean showArchived, long languageId) throws DotDataException, DotStateException, DotSecurityException{
	        Map<String, Object> pageMap = new HashMap<>(page.getMap());

            pageMap.put("mimeType", "application/dotpage");
            pageMap.put("name", page.getPageUrl());
            pageMap.put("description", page.getFriendlyName());
            pageMap.put("extension", "page");
            pageMap.put("isContentlet", true);
            pageMap.put("title",  page.getPageUrl());

            pageMap.put("identifier", page.getIdentifier());
            pageMap.put("inode", page.getInode());
            pageMap.put("languageId", ((Contentlet)page).getLanguageId());
            
            Language lang = APILocator.getLanguageAPI().getLanguage(((Contentlet)page).getLanguageId());
            
            pageMap.put("languageCode", lang.getLanguageCode());
            pageMap.put("countryCode", lang.getCountryCode());
            pageMap.put("isLocked", page.isLocked());
            pageMap.put("languageFlag", LanguageUtil.getLiteralLocale(lang.getLanguageCode(), lang.getCountryCode()));
        


            pageMap.put("hasLiveVersion", APILocator.getVersionableAPI().hasLiveVersion(page));
            pageMap.put("statusIcons", UtilHTML.getStatusIcons(page));
            pageMap.put("hasTitleImage",String.valueOf(((Contentlet)page).getTitleImage().isPresent()));
            pageMap.put("__icon__", "pageIcon");

	
	        return pageMap;
	   }
	
	
	private Map<String,Object> fileAssetMap(FileAsset fileAsset, User user, boolean showArchived) throws DotDataException, DotStateException, DotSecurityException{
	    Map<String, Object> fileMap = new HashMap<>(fileAsset.getMap());

        Identifier ident = APILocator.getIdentifierAPI().find(
                fileAsset.getVersionId());


        fileMap.put("mimeType", APILocator.getFileAssetAPI()
                .getMimeType(fileAsset.getUnderlyingFileName()));
        fileMap.put("name", ident.getAssetName());
        fileMap.put("title",  ident.getAssetName());
        fileMap.put("fileName", ident.getAssetName());
        fileMap.put("title", fileAsset.getFriendlyName());
        fileMap.put("description", fileAsset instanceof Contentlet ?
                                   ((Contentlet)fileAsset).getStringProperty(FileAssetAPI.DESCRIPTION)
                                   : "");
        fileMap.put("extension", UtilMethods
                .getFileExtension(fileAsset.getUnderlyingFileName()));
        fileMap.put("path", fileAsset.getPath());
        fileMap.put("type", "file_asset");
        Host hoster = APILocator.getHostAPI().find(ident.getHostId(), APILocator.systemUser(), false);
        fileMap.put("hostName", hoster.getHostname());
        
        

        fileMap.put("size", fileAsset.getFileSize());
        fileMap.put("publishDate", fileAsset.getIDate());
        // BEGIN GRAZIANO issue-12-dnd-template
        fileMap.put(
                "parent",
                fileAsset.getParent() != null ? fileAsset
                        .getParent() : "");


        fileMap.put("identifier", fileAsset.getIdentifier());
        fileMap.put("inode", fileAsset.getInode());
        fileMap.put("isLocked", fileAsset.isLocked());
        fileMap.put("isContentlet", true);
        Language lang = langAPI.getLanguage(fileAsset.getLanguageId());

        fileMap.put("languageId", lang.getId());
        fileMap.put("languageCode", lang.getLanguageCode());
        fileMap.put("countryCode", lang.getCountryCode());
        fileMap.put("languageFlag", LanguageUtil.getLiteralLocale(lang.getLanguageCode(), lang.getCountryCode()));

        fileMap.put("hasLiveVersion", APILocator.getVersionableAPI().hasLiveVersion(fileAsset));
        fileMap.put("statusIcons", UtilHTML.getStatusIcons(fileAsset));
        fileMap.put("hasTitleImage",String.valueOf(fileAsset.getTitleImage().isPresent()));
        fileMap.put("__icon__", UtilHTML.getIconClass(fileAsset ));
        return fileMap;
	    
	    
	}
	
	
	
	
	
	   private Map<String,Object> dotAssetMap(Contentlet dotAsset, User user, boolean showArchived) throws DotDataException, DotStateException, DotSecurityException{
	        Map<String, Object> fileMap = new  ContentletToMapTransformer(dotAsset).toMaps().get(0);
	       
            Identifier ident = APILocator.getIdentifierAPI().find(
                            dotAsset.getVersionId());
	        final String fileName=Try.of(()->dotAsset.getBinary("asset").getName()).getOrElse("unknown");

	        fileMap.put("mimeType", APILocator.getFileAssetAPI()
	                .getMimeType(fileName));
	        fileMap.put("name", fileName);
	        fileMap.put("title",  fileName);
	        fileMap.put("fileName", fileName);
	        fileMap.put("title", fileName);
	        fileMap.put("friendyName", "");
	       
	        
	        fileMap.put("extension", UtilMethods.getFileExtension(fileName));
	        fileMap.put("path", "/dA/" + ident.getId() + "/" + fileName);
	        fileMap.put("type", "dotasset");
	        Host hoster = APILocator.getHostAPI().find(ident.getHostId(), APILocator.systemUser(), false);
	        fileMap.put("hostName", hoster.getHostname());
	        

	        fileMap.put("size", Try.of(()->dotAsset.getBinary("asset").length()).getOrElse(0l));
	        fileMap.put("publishDate", dotAsset.getModDate());

	        fileMap.put("isContentlet", true);


	        fileMap.put("identifier", dotAsset.getIdentifier());
	        fileMap.put("inode", dotAsset.getInode());
	        fileMap.put("isLocked", dotAsset.isLocked());
	        fileMap.put("isContentlet", true);
	        Language lang = langAPI.getLanguage(dotAsset.getLanguageId());

	        fileMap.put("languageId", lang.getId());
	        fileMap.put("languageCode", lang.getLanguageCode());
	        fileMap.put("countryCode", lang.getCountryCode());
	        fileMap.put("languageFlag", LanguageUtil.getLiteralLocale(lang.getLanguageCode(), lang.getCountryCode()));

	        fileMap.put("hasLiveVersion", APILocator.getVersionableAPI().hasLiveVersion(dotAsset));
	        fileMap.put("statusIcons", UtilHTML.getStatusIcons(dotAsset));
	        fileMap.put("hasTitleImage",String.valueOf(dotAsset.getTitleImage().isPresent()));
	        fileMap.put("__icon__", UtilHTML.getIconClass(dotAsset));
	        return fileMap;
	        
	        
	    }
	    
	
	
	
	
	
	
}
