package com.powertac.tourney.beans;

import java.util.List;
import java.util.Vector;

import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;

@ApplicationScoped
@ManagedBean
public class Tournaments {
	private static final String key = "tournaments";
	
	private Vector<Tournament> tournaments;
	
	private String sortColumn = null;
	private boolean sortAscending = true;
	private int rowCount = 5;
	
	
	public Tournaments(){
		tournaments = new Vector<Tournament>();
	}

	public static String getKey() {
		return key;
	}
	public static Tournaments getAllTournaments(){
		return (Tournaments) FacesContext.getCurrentInstance()
		.getExternalContext().getApplicationMap().get(Tournaments.getKey());
	}
	
	public void addTournament(Tournament t){
		this.tournaments.add(t);
	}
	
	public Tournament[] getTournamentList(){
		if(tournaments.size() == 0){
			return null;
		}else{
			Tournament[] newTourney = new Tournament[tournaments.size()];
			int i = 0;
			for(Tournament t : tournaments){
				newTourney[i] = t;
				i++;
			}
			
			return newTourney;
		}
	}
	
	public List<Tournament> getLists(){
		return (List<Tournament>) tournaments;
	}
	
	public Tournament getTournamentById(int id){
		for (Tournament t : tournaments){
			if(t.getTournamentId() == id){
				return t;
			}
		}
		return null;
	}
	

	public String getSortColumn() {
		return sortColumn;
	}

	public void setSortColumn(String sortColumn) {
		this.sortColumn = sortColumn;
	}

	public boolean isSortAscending() {
		return sortAscending;
	}

	public void setSortAscending(boolean sortAscending) {
		this.sortAscending = sortAscending;
	}

	public int getRowCount() {
		return rowCount;
	}

	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}
}
