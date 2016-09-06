package com.dianping.pigeon.governor.service.impl;

import com.dianping.pigeon.governor.dao.ServiceNodeMapper;
import com.dianping.pigeon.governor.model.Project;
import com.dianping.pigeon.governor.model.ServiceNode;
import com.dianping.pigeon.governor.model.ServiceNodeExample;
import com.dianping.pigeon.governor.service.ProjectOwnerService;
import com.dianping.pigeon.governor.service.ProjectService;
import com.dianping.pigeon.governor.service.ServiceNodeService;
import com.dianping.pigeon.governor.util.ThreadPoolFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Created by chenchongze on 16/7/6.
 */
@Service
public class ServiceNodeServiceImpl implements ServiceNodeService {

    private Logger logger = LogManager.getLogger();

    private ExecutorService proOwnerThreadPool = ThreadPoolFactory.getWorkThreadPool();

    @Autowired
    private ServiceNodeMapper serviceNodeMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectOwnerService projectOwnerService;

    private List<ServiceNode> serviceNodesCache;
    private long serviceNodesCacheLastUpdateTime = 0;
    private long checkCacheInternal = 20000;

    /**
     *
     * @param projectName
     * @param serviceName
     * @param group
     * @param ip
     * @param port
     * @param updatezk
     * @return true when succeed, false when failed
     * @throws Exception
     */
    @Override
    public boolean publishService(String projectName, String serviceName, String group, String ip, String port, String updatezk) {
        try {
            if ("true".equalsIgnoreCase(updatezk)) {
                //todo update zk
            }

            ServiceNode serviceNode = getServiceNode(serviceName, group, ip, port);

            if (serviceNode != null) {
                if (serviceNode.getProjectName().equals(projectName)) {
                    logger.warn("existing service node: " + serviceNode);
                } else {
                    serviceNode.setProjectName(projectName);
                    serviceNodeMapper.updateByPrimaryKeySelective(serviceNode);
                }
            } else {
                ServiceNode newServiceNode = new ServiceNode();
                newServiceNode.setServiceName(serviceName);
                newServiceNode.setGroup(group);
                newServiceNode.setIp(ip);
                newServiceNode.setPort(port);
                newServiceNode.setProjectName(projectName);
                serviceNodeMapper.insertSelective(newServiceNode);
            }

            createProject(projectName);

            return true;
        } catch (DataAccessException e) {
            logger.error(e);
        } catch (Throwable t) {
            logger.error(t);
        }

        return false;
    }

    @Override
    public boolean unpublishService(String serviceName, String group, String ip, String port, String updatezk) {
        try {
            if ("true".equalsIgnoreCase(updatezk)) {
                //todo update zk
            }

            ServiceNode serviceNode = getServiceNode(serviceName, group, ip, port);

            if (serviceNode != null) {
                serviceNodeMapper.deleteByPrimaryKey(serviceNode.getId());
            } else {
                logger.warn("service node not existed! " + serviceName + "#" + group + ", " + ip + ":" + port);
            }

            return true;
        } catch (DataAccessException e) {
            logger.error(e);
        } catch (Throwable t) {
            logger.error(t);
        }

        return false;
    }

    /**
     *
     * @param serviceName
     * @param group
     * @param ip
     * @param port
     * @return serviceNode in database, null if not existed, null if retrieve database error
     */
    @Override
    public ServiceNode getServiceNode(String serviceName, String group, String ip, String port) {
        ServiceNodeExample serviceNodeExample = new ServiceNodeExample();
        serviceNodeExample.createCriteria()
                .andServiceNameEqualTo(serviceName)
                .andGroupEqualTo(group)
                .andIpEqualTo(ip)
                .andPortEqualTo(port);

        List<ServiceNode> serviceNodes = new ArrayList<ServiceNode>();

        try {
            serviceNodes = serviceNodeMapper.selectByExample(serviceNodeExample);
        } catch (DataAccessException e) {
            logger.error(e);
        }

        if(serviceNodes.size() > 0) {
            return serviceNodes.get(0);
        }

        return null;
    }

    @Override
    public List<ServiceNode> getServiceNode(String serviceName, String group) {
        ServiceNodeExample serviceNodeExample = new ServiceNodeExample();
        serviceNodeExample.createCriteria()
                .andServiceNameEqualTo(serviceName)
                .andGroupEqualTo(group);

        List<ServiceNode> serviceNodes = new ArrayList<ServiceNode>();

        try {
            serviceNodes = serviceNodeMapper.selectByExample(serviceNodeExample);
        } catch (DataAccessException e) {
            logger.error(e);
        }

        return serviceNodes;
    }

    @Override
    public List<ServiceNode> retrieveAllByProjectName(String projectName) {
        List<ServiceNode> serviceNodes = new ArrayList<ServiceNode>();

        if(StringUtils.isNotBlank(projectName)) {
            ServiceNodeExample serviceNodeExample = new ServiceNodeExample();
            serviceNodeExample.createCriteria().andProjectNameEqualTo(projectName);

            try {
                serviceNodes = serviceNodeMapper.selectByExample(serviceNodeExample);
            } catch (DataAccessException e) {
                logger.error(e);
            }
        }

        return serviceNodes;
    }

    @Override
    public List<ServiceNode> retrieveAllByServiceNameAndGroup(String serviceName, String group) {
        List<ServiceNode> serviceNodes = new ArrayList<ServiceNode>();

        if(StringUtils.isNotBlank(serviceName) && group != null) {
            ServiceNodeExample serviceNodeExample = new ServiceNodeExample();
            serviceNodeExample.createCriteria()
                    .andServiceNameEqualTo(serviceName)
                    .andGroupEqualTo(group);

            try {
                serviceNodes = serviceNodeMapper.selectByExample(serviceNodeExample);
            } catch (DataAccessException e) {
                logger.error(e);
            }
        }

        return serviceNodes;
    }

    @Override
    public int createServiceNode(ServiceNode serviceNode) {
        int sqlSucCount = 0;

        try {
            sqlSucCount = serviceNodeMapper.insertSelective(serviceNode);
        } catch (DataAccessException e) {
            logger.error(e);
            sqlSucCount = -1;
        }

        return sqlSucCount;
    }

    @Override
    public int deleteServiceNodeById(ServiceNode serviceNode) {
        int sqlSucCount = 0;
        Integer id = serviceNode.getId();

        if (id != null) {
            try {
                sqlSucCount = serviceNodeMapper.deleteByPrimaryKey(id);
            } catch (DataAccessException e) {
                logger.error(e);
                sqlSucCount = -1;
            }
        }

        return sqlSucCount;
    }

    @Override
    public List<ServiceNode> retrieveAll() {
        return serviceNodeMapper.selectByExample(null);
    }

    @Override
    public List<ServiceNode> retrieveAllIdNamesByCache() {
        long currentTime = System.currentTimeMillis();
        if(currentTime - serviceNodesCacheLastUpdateTime > checkCacheInternal){
            serviceNodesCache = retrieveAll();
            serviceNodesCacheLastUpdateTime = currentTime;
        }

        return serviceNodesCache;
    }

    @Override
    public int deleteServiceNode(String serviceName, String group, String ip, String port) {
        int count = 0;

        ServiceNodeExample serviceNodeExample = new ServiceNodeExample();
        serviceNodeExample.createCriteria()
                .andServiceNameEqualTo(serviceName)
                .andGroupEqualTo(group)
                .andIpEqualTo(ip)
                .andPortEqualTo(port);

        try {
            count = serviceNodeMapper.deleteByExample(serviceNodeExample);
        } catch (DataAccessException e) {
            logger.error(e);
            count = -1;
        }

        return count;
    }

    private void createProject(final String projectName) {
        try {
            Project newProject = projectService.findProject(projectName);

            if(newProject == null){
                newProject = projectService.createProject(projectName, true);
            }

            if (newProject == null ) {
                logger.warn("failed to create project: " + projectName);
                return ;
            }

            final String emails = newProject.getEmail();

            proOwnerThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    //create default project owner
                    projectOwnerService.createDefaultOwner(emails, projectName);
                }
            });
        } catch (DataAccessException e) {
            logger.error(e);
        }
    }
}
