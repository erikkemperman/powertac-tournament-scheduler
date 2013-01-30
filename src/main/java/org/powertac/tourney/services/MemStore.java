package org.powertac.tourney.services;

import org.apache.log4j.Logger;
import org.powertac.tourney.beans.Config;
import org.powertac.tourney.beans.Machine;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;


@Service("memStore")
public class MemStore
{
  private static Logger log = Logger.getLogger("TMLogger");

  public static HashMap<String, List<String>> machineIPs;
  public static HashMap<String, String> vizIPs;
  public static HashMap<String, String> localIPs;

  public static HashMap<Integer, List<Long>> brokerCheckins;
  public static HashMap<String, Long> vizCheckins;
  public static HashMap<Integer, String[]> gameHeartbeats;
  public static HashMap<Integer, Integer> gameLengths;

  public static HashMap<Integer, Boolean> brokerState = new HashMap<Integer, Boolean>();

  public static String indexContent;

  public MemStore ()
  {
    machineIPs = null;
    vizIPs = null;
    localIPs = null;

    brokerCheckins = new HashMap<Integer, List<Long>>();
    vizCheckins = new HashMap<String, Long>();
    gameHeartbeats = new HashMap<Integer, String[]>();
    gameLengths = new HashMap<Integer, Integer>();
  }

  public static void getIpAddresses ()
  {
    machineIPs = new HashMap<String, List<String>>();
    vizIPs = new HashMap<String, String>();
    localIPs = new HashMap<String, String>();

    for (Machine m: Machine.getMachineList()) {
      try {
        String machineIP = InetAddress.getByName(m.getMachineUrl()).toString();
        if (machineIP.contains("/")) {
          machineIP = machineIP.split("/")[1];
        }

        List<String> machine =
            Arrays.asList(m.getMachineName(), m.getMachineId().toString());
        machineIPs.put(machineIP, machine);
      } catch (UnknownHostException ignored) {
      }

      try {
        String vizIP = InetAddress.getByName(
            m.getVizUrl().split(":")[0].split("/")[0]).toString();
        if (vizIP.contains("/")) {
          vizIP = vizIP.split("/")[1];
        }
        vizIPs.put(vizIP, m.getMachineName());
      } catch (UnknownHostException ignored) {
      }
    }

    localIPs.put("127.0.0.1", "loopback");
    try {
      for (Enumeration<NetworkInterface> en =
               NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
        NetworkInterface intf = en.nextElement();

        if (!intf.getName().startsWith("eth")) {
          continue;
        }

        for (Enumeration<InetAddress> enumIpAddr =
                 intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
          String ip = enumIpAddr.nextElement().toString();
          if (ip.contains(":")) {
            continue;
          }
          if (ip.contains("/")) {
            ip = ip.split("/")[1];
          }
          localIPs.put(ip, intf.getName());
        }
      }
    } catch (SocketException e) {
      log.error(" (error retrieving network interface list)");
    }
  }

  public static boolean checkMachineAllowed (String slaveAddress)
  {
    //log.debug("Testing checkMachineAllowed : " + slaveAddress);

    if (machineIPs == null) {
      getIpAddresses();
    }

    assert localIPs != null;
    if (localIPs.containsKey(slaveAddress)) {
      //log.debug("Localhost is always allowed");
      return true;
    }

    assert machineIPs != null;
    if (machineIPs.containsKey(slaveAddress)) {
      //log.debug(slaveAddress + " is allowed");
      return true;
    }

    log.debug(slaveAddress + " is not allowed");
    return false;
  }

  public static boolean checkVizAllowed (String vizAddress)
  {
    //log.debug("Testing checkVizAllowed : " + vizAddress);

    if (vizIPs == null) {
      getIpAddresses();
    }

    assert localIPs != null;
    if (localIPs.containsKey(vizAddress)) {
      //log.debug("Localhost is always allowed");
      return true;
    }

    assert vizIPs != null;
    if (vizIPs.containsKey(vizAddress)) {
      //log.debug(vizAddress + " is allowed");
      return true;
    }

    log.debug(vizAddress + " is not allowed");
    return true;
  }

  public synchronized static void addBrokerCheckin (int brokerId)
  {
    List<Long> dates = brokerCheckins.get(brokerId);
    if (dates == null) {
      dates = new ArrayList<Long>();
    }

    dates.add(System.currentTimeMillis());

    if (dates.size() > 4) {
      dates.remove(0);
    }

    brokerCheckins.put(brokerId, dates);
  }

  public synchronized static void addVizCheckin (String machineName)
  {
    vizCheckins.put(machineName, System.currentTimeMillis());
  }

  public synchronized static void addGameHeartbeat (int gameId, String message)
  {
    gameHeartbeats.put(gameId,
        new String[]{message, System.currentTimeMillis() + ""});
  }

  public synchronized static void removeGameHeartbeat (int gameId)
  {
    if (gameHeartbeats.containsKey(gameId)) {
      gameHeartbeats.remove(gameId);
    }
  }

  public synchronized static void addGameLength (int gameId, String gameLength)
  {
    try {
      gameLengths.put(gameId, Integer.parseInt(gameLength));
    } catch (Exception ignored) {
    }
  }

  public synchronized static void removeGameLength (int gameId)
  {
    if (gameLengths.containsKey(gameId)) {
      gameLengths.remove(gameId);
    }
  }

  public static boolean getBrokerState (int brokerId)
  {
    boolean enabled = true;
    try {
      enabled = MemStore.brokerState.get(brokerId);
    } catch (Exception ignored) {
    }

    return enabled;
  }

  public static void setBrokerState (int brokerId, boolean state)
  {
    brokerState.put(brokerId, state);
  }

  public static String getIndexContent ()
  {
    if (indexContent == null || indexContent.isEmpty()) {
      indexContent = Config.getIndexContent();
      if (indexContent == null) {
        return "Error connecting to DB";
      }
    }

    return indexContent;
  }

  public static boolean setIndexContent (String newContent)
  {
    indexContent = newContent;

    return Config.setIndexContent(newContent);
  }
}