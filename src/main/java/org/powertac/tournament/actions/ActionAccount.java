package org.powertac.tournament.actions;

import org.apache.commons.codec.digest.DigestUtils;
import org.powertac.tournament.beans.Broker;
import org.powertac.tournament.beans.User;
import org.powertac.tournament.services.Utils;
import org.springframework.beans.factory.InitializingBean;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@ManagedBean
public class ActionAccount implements InitializingBean
{
  private User user = User.getCurrentUser();
  private String brokerName;
  private String brokerShort;

  private List<Broker> brokers;

  public ActionAccount ()
  {
  }

  @SuppressWarnings("unchecked")
  public void afterPropertiesSet () throws Exception
  {
    brokers = new ArrayList<Broker>(user.getBrokerMap().values());
    Collections.sort(brokers, new Utils.AlphanumComparator());
  }

  public void addBroker ()
  {
    // Check if name and description not empty, and if name allowed
    if (namesEmpty(brokerName, brokerShort, 2) ||
        nameExists(brokerName, -1, 2) ||
        nameAllowed(brokerName, 2)) {
      return;
    }

    if (user.isEditingBroker() || !user.isLoggedIn()) {
      return;
    }

    String brokerAuth = DigestUtils.md5Hex(brokerName +
        (new Date()).toString() + Math.random());

    Broker broker = new Broker();
    broker.setBrokerAuth(brokerAuth);
    broker.setBrokerName(brokerName);
    broker.setShortDescription(brokerShort);
    broker.setUser(user);

    boolean added = broker.save();
    if (added) {
      brokerName = "";
      brokerShort = "";
      brokers = new ArrayList<Broker>();
      User.reloadUser(user);
    }
    else {
      message(2, "Error adding broker");
    }
  }

  public void deleteBroker (Broker broker)
  {
    User user = User.getCurrentUser();
    if (user.isEditingBroker() || !user.isLoggedIn()) {
      return;
    }

    brokers = new ArrayList<Broker>();
    boolean deleted = broker.delete();

    if (deleted) {
      User.reloadUser(user);
    }
    else {
      message(1, "Error deleting broker");
    }
  }

  public void updateBroker (Broker broker)
  {
    // Check if name and description not empty, and if name allowed (if changed)
    if (namesEmpty(broker.getBrokerName(), broker.getShortDescription(), 1)) {
      return;
    }
    if (nameAllowed(broker.getBrokerName(), 1)) {
      return;
    }
    if (nameExists(broker.getBrokerName(), broker.getBrokerId(), 1)) {
      return;
    }

    User user = User.getCurrentUser();
    if (!user.isLoggedIn()) {
      return;
    }

    String errorMessage = broker.update();
    if (errorMessage != null) {
      message(1, errorMessage);
    }
    else {
      user.setEditingBroker(false);
      broker.setEdit(false);
    }
  }

  public void editBroker (Broker broker)
  {
    User.getCurrentUser().setEditingBroker(true);
    broker.setEdit(true);
  }

  public void cancelEdit (Broker broker)
  {
    User.getCurrentUser().setEditingBroker(false);
    broker.setEdit(false);
  }

  private boolean namesEmpty (String name, String description, int field)
  {
    if (name == null || description == null ||
        name.trim().isEmpty() || description.trim().isEmpty()) {
      message(field, "Broker requires a Name and a Description");
      return true;
    }
    return false;
  }

  private boolean nameExists (String brokerName, int brokerId, int field)
  {
    Broker broker = Broker.getBrokerByName(brokerName);
    if (broker == null || broker.getBrokerId() == brokerId) {
      return false;
    }
    message(field, "Brokername taken, please select a new name");
    return true;
  }

  // We can't allow commas, used in end-of-game message from server
  private boolean nameAllowed (String brokerName, int field)
  {
    Pattern ALPHANUMERIC = Pattern.compile("[A-Za-z0-9\\-\\_]+");
    Matcher m = ALPHANUMERIC.matcher(brokerName);

    if (!m.matches()) {
      message(field, "Brokername contains illegal characters, please select a "
          + "new name (only alphanumeric, '-' and '_' allowed)");
      return true;
    }
    return false;
  }

  public void editUserDetails ()
  {
    user.setEditingDetails(true);
  }

  public void saveUserDetails ()
  {
    user.save();
    user.setEditingDetails(false);
  }

  private void message (int field, String msg)
  {
    FacesMessage fm = new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null);
    if (field == 0) {
      FacesContext.getCurrentInstance().addMessage("accountForm0", fm);
    }
    else if (field == 1) {
      FacesContext.getCurrentInstance().addMessage("accountForm1", fm);
    }
    else if (field == 2) {
      FacesContext.getCurrentInstance().addMessage("accountForm2", fm);
    }
  }

  public List<Broker> getBrokers ()
  {
    return brokers;
  }

  //<editor-fold desc="Setters and Getters">
  public String getBrokerShort ()
  {
    return brokerShort;
  }
  public void setBrokerShort (String brokerShort)
  {
    this.brokerShort = brokerShort;
  }

  public String getBrokerName ()
  {
    return brokerName;
  }
  public void setBrokerName (String brokerName)
  {
    this.brokerName = brokerName;
  }

  public User getUser ()
  {
    return user;
  }
  public void setUser (User user)
  {
    this.user = user;
  }
  //</editor-fold>
}
