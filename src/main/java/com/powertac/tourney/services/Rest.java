package com.powertac.tourney.services;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.springframework.stereotype.Service;

import com.powertac.tourney.beans.Game;
import com.powertac.tourney.beans.Games;
import com.powertac.tourney.beans.Machines;
import com.powertac.tourney.beans.Scheduler;
import com.powertac.tourney.beans.Tournaments;
import com.powertac.tourney.constants.*;

@Service
public class Rest{
	public static String parseBrokerLogin(Map<?, ?> params){
		String responseType = ((String[]) params.get(Constants.REQ_PARAM_TYPE))[0];
		String brokerAuthToken = ((String[]) params.get(Constants.REQ_PARAM_AUTH_TOKEN))[0];
		String competitionName = ((String []) params.get(Constants.REQ_PARAM_JOIN))[0];
		
		String retryResponse;
		String loginResponse;
		String doneResponse;
		
		if(responseType.equalsIgnoreCase("xml")){
			retryResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<message><retry>%d</retry></message>";
			loginResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<message><login><jmsUrl>%s</jmsUrl><gameToken>%s</gameToken></login></message>";
			doneResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<message><done></done></message>";			
		}else{
			retryResponse = "{\n \"retry\":%d\n}";
			loginResponse = "{\n \"login\":%d\n \"jmsUrl\":%s\n \"gameToken\":%s\n}";
			doneResponse = "{\n \"done\":\"true\"\n}";
		}
		if(competitionName != null && Games.getAllGames().getGameList() != null){
			for (Game g : Games.getAllGames().getGameList()){
				// Only consider games that have started and are ready for brokers to join
				if(g.getStartTime().before(new Date()) && g.getStatus().equalsIgnoreCase("ready")){
					//Anyone can start and join a test competition
					if(competitionName.equalsIgnoreCase("test")){
						// Spawn a new test competition and rerun
						Game game = new Game();
						game.setBootstrapUrl("http://www.cselabs.umn.edu/~onarh001/bootstraprun.xml");
						game.setCompetitionName("test");
						game.setMaxBrokers(1);
						game.setStartTime(new Date());
						game.setPomUrl("");
						game.setServerConfigUrl("");
						game.addBrokerLogin("anybroker", brokerAuthToken);
						Scheduler.getScheduler().schedule(new StartServer(game,Machines.getAllMachines(),Tournaments.getAllTournaments()), new Date());						
						return String.format(retryResponse,5);
					}else if(competitionName.equalsIgnoreCase(g.getCompetitionName()) && g.isBrokerRegistered(brokerAuthToken)){
						// If a broker is registered and knows the competition name, give them an the jmsUrl and gameToken to login
						return String.format(loginResponse, g.getJmsUrl(),"1234");
					}
				}
				// If the game has yet to start and broker is registered send retry message
				if(g.isBrokerRegistered(brokerAuthToken)){
					System.out.println("Broker: " + brokerAuthToken + " attempted to log in, game has not started-sending retry");
					long retry = g.getStartTime().getTime()-(new Date()).getTime();
					
					return String.format(retryResponse, retry > 0 ? retry : 20);
				}
				
				
			}
		}
		return doneResponse;
	}
	
	public static String parseServerInterface(Map<?, ?> params){
		if(params!=null){
			String actionString = ((String[]) params.get(Constants.REQ_PARAM_ACTION))[0];
			
			if(actionString.equalsIgnoreCase("status")){
				String statusString = ((String[]) params.get(Constants.REQ_PARAM_STATUS))[0];
				String gameIdString = ((String[]) params.get(Constants.REQ_PARAM_GAME_ID))[0];
				int gameId = Integer.parseInt(gameIdString);
				
				if(statusString.equalsIgnoreCase("bootstrap-running")){
					System.out.println("Recieved bootstrap running message from game: "+ gameId);
					Database db = new Database();
					try {
						db.updateGameStatusById(gameId, "in-progress");
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}else if(statusString.equalsIgnoreCase("bootstrap-done")){
					System.out.println("Recieved bootstrap done message from game: " + gameId);
					//Database db = new Database();
					
					
					
					//db.updateGameBootstrapById(gameId, bootstrapUrl);
					
					
				}else if(statusString.equalsIgnoreCase("game-ready")){
					
				}else if(statusString.equalsIgnoreCase("game-running")){
										
				}else if(statusString.equalsIgnoreCase("game-done")){
					
				}else{
					return "ERROR";
				}
			
		
			}
		}
		return "Not Yet Implementented";	
	}
	
	/***
	 * Returns a properties file string
	 * @param params
	 * @return String representing a properties file
	 */
	public static String parseProperties(Map<?, ?> params){
		String gameId = "0";
		if(params!=null){
			try{
				gameId = ((String[]) params.get(Constants.REQ_PARAM_GAME_ID))[0];
			}catch(Exception e){
				
			}
		}
		
		Database db = new Database();
		List<String> props = new ArrayList<String>();
		
		props = CreateProperties.getPropertiesForGameId(Integer.parseInt(gameId));
		
		String result = "";
		
		//Location of weather data
		String weatherLocation = "server.weatherService.weatherLocation = ";
		// Simulation base time
		String startTime = "common.competition.simulationBaseTime = ";
		
		if(props.size()==2){
			result += weatherLocation + props.get(0) +"\n"; 
			result += startTime + props.get(1);
		}
		
		
		return result;
	}
	
	
	

}