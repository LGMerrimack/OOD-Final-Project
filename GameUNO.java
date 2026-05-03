import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;

public class GameUNO {

	private static final int STARTING_HAND_SIZE = 7;
	private static final int DEFAULT_AI_PLAYERS = 2;

	private final Random random = new Random();
	private final Scanner scanner;
	private final List<Player> players = new ArrayList<>();
	private final List<Card> drawPile = new ArrayList<>();
	private final Queue<Card> discardBuffer = new LinkedList<>();

	private Card topCard;
	private int currentPlayerIndex;

	private enum CardColor {
		RED,
		YELLOW,
		GREEN,
		BLUE,
		WILD
	}

	private static class Card {
		private final CardColor color;
		private final int value;

		Card(CardColor color, int value) {
			this.color = color;
			this.value = value;
		}

		CardColor getColor() {
			return color;
		}

		int getValue() {
			return value;
		}

		boolean isWildcard() {
			return color == CardColor.WILD;
		}

		@Override
		public String toString() {
			if (isWildcard()) {
				return "WILD";
			}
			return color.name() + " " + value;
		}
	}

	private static class Player {
		private final String name;
		private final boolean human;
		private final List<Card> hand = new ArrayList<>();

		Player(String name, boolean human) {
			this.name = name;
			this.human = human;
		}
	}

	public GameUNO() {
		this(DEFAULT_AI_PLAYERS, new Scanner(System.in));
	}

	public GameUNO(int aiPlayers, Scanner scanner) {
		if (aiPlayers < 1) {
			throw new IllegalArgumentException("At least one AI opponent is required.");
		}
		this.scanner = scanner;
		players.add(new Player("You", true));
		for (int i = 1; i <= aiPlayers; i++) {
			players.add(new Player("Opponent " + i, false));
		}
	}

	public void play() {
		setupGame();
		boolean gameOver = false;

		while (!gameOver) {
			Player currentPlayer = players.get(currentPlayerIndex);
			printGameState(currentPlayer);

			if (currentPlayer.human) {
				handleHumanTurn(currentPlayer);
			} else {
				handleAiTurn(currentPlayer);
			}

			if (currentPlayer.hand.isEmpty()) {
				System.out.println("\n" + currentPlayer.name + " wins the game!");
				gameOver = true;
			} else {
				currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
			}
		}
	}

	private void setupGame() {
		buildDeck();
		Collections.shuffle(drawPile, random);

		for (Player player : players) {
			for (int i = 0; i < STARTING_HAND_SIZE; i++) {
				player.hand.add(drawCard());
			}
		}

		do {
			topCard = drawCard();
			if (topCard == null) {
				refillDrawPile();
			}
		} while (topCard != null && topCard.isWildcard());

		if (topCard == null) {
			throw new IllegalStateException("Deck setup failed.");
		}

		currentPlayerIndex = 0;
	}

	private void buildDeck() {
		drawPile.clear();

		for (CardColor color : CardColor.values()) {
			if (color == CardColor.WILD) {
				for (int i = 0; i < 4; i++) {
					drawPile.add(new Card(CardColor.WILD, -1));
				}
				continue;
			}

			drawPile.add(new Card(color, 0));
			for (int value = 1; value <= 9; value++) {
				drawPile.add(new Card(color, value));
				drawPile.add(new Card(color, value));
			}
		}
	}

	private Card drawCard() {
		if (drawPile.isEmpty()) {
			refillDrawPile();
		}
		if (drawPile.isEmpty()) {
			return null;
		}
		return drawPile.remove(drawPile.size() - 1);
	}

	private void refillDrawPile() {
		while (!discardBuffer.isEmpty()) {
			drawPile.add(discardBuffer.poll());
		}
		Collections.shuffle(drawPile, random);
	}

	private void printGameState(Player currentPlayer) {
		System.out.println("\n================================");
		System.out.println("Top card: " + topCard);
		System.out.println("Cards left:");
		for (Player player : players) {
			if (!player.human) {
				System.out.println("- " + player.name + ": " + player.hand.size());
			}
		}

		if (currentPlayer.human) {
			System.out.println("\nYour hand:");
			for (int i = 0; i < currentPlayer.hand.size(); i++) {
				System.out.println((i + 1) + ") " + currentPlayer.hand.get(i));
			}
			System.out.println("Type a card number to play, or D to draw.");
		} else {
			System.out.println("\n" + currentPlayer.name + " is taking a turn...");
		}
	}

	private void handleHumanTurn(Player player) {
		while (true) {
			System.out.print("> ");
			String input = scanner.nextLine().trim();

			if (input.equalsIgnoreCase("d")) {
				Card drawn = drawCard();
				if (drawn == null) {
					System.out.println("No cards left to draw. Turn skipped.");
					return;
				}

				System.out.println("You drew: " + drawn);
				player.hand.add(drawn);

				if (canPlay(drawn, topCard)) {
					System.out.println("You can play the drawn card. Play it now? (y/n)");
					String playDrawn = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
					if (playDrawn.startsWith("y")) {
						playCard(player, player.hand.size() - 1);
					}
				}
				return;
			}

			int choice;
			try {
				choice = Integer.parseInt(input);
			} catch (NumberFormatException e) {
				System.out.println("Enter a valid card number or D to draw.");
				continue;
			}

			if (choice < 1 || choice > player.hand.size()) {
				System.out.println("That card number is out of range.");
				continue;
			}

			Card selected = player.hand.get(choice - 1);
			if (!canPlay(selected, topCard)) {
				System.out.println("Illegal move. You must match number, color, or play a wildcard.");
				continue;
			}

			playCard(player, choice - 1);
			return;
		}
	}

	private void handleAiTurn(Player player) {
		for (int i = 0; i < player.hand.size(); i++) {
			Card candidate = player.hand.get(i);
			if (canPlay(candidate, topCard)) {
				playCard(player, i);
				System.out.println(player.name + " played: " + topCard);
				return;
			}
		}

		Card drawn = drawCard();
		if (drawn == null) {
			System.out.println(player.name + " could not draw and skips the turn.");
			return;
		}

		player.hand.add(drawn);
		System.out.println(player.name + " drew a card.");

		if (canPlay(drawn, topCard)) {
			playCard(player, player.hand.size() - 1);
			System.out.println(player.name + " played the drawn card: " + topCard);
		}
	}

	private void playCard(Player player, int handIndex) {
		Card played = player.hand.remove(handIndex);

		if (topCard != null) {
			discardBuffer.offer(topCard);
		}

		if (played.isWildcard() && player.human) {
			CardColor chosen = promptForColor();
			topCard = new Card(chosen, -1);
			System.out.println("You changed the color to " + chosen + ".");
			return;
		}

		if (played.isWildcard()) {
			CardColor chosen = pickAiColor(player);
			topCard = new Card(chosen, -1);
			return;
		}

		topCard = played;
	}

	private CardColor promptForColor() {
		while (true) {
			System.out.println("Choose a color (red, yellow, green, blue):");
			System.out.print("> ");
			String colorText = scanner.nextLine().trim().toLowerCase(Locale.ROOT);

			switch (colorText) {
				case "red":
					return CardColor.RED;
				case "yellow":
					return CardColor.YELLOW;
				case "green":
					return CardColor.GREEN;
				case "blue":
					return CardColor.BLUE;
				default:
					System.out.println("Invalid color.");
			}
		}
	}

	private CardColor pickAiColor(Player player) {
		int[] counts = new int[4];
		for (Card card : player.hand) {
			if (card.getColor() == CardColor.RED) {
				counts[0]++;
			} else if (card.getColor() == CardColor.YELLOW) {
				counts[1]++;
			} else if (card.getColor() == CardColor.GREEN) {
				counts[2]++;
			} else if (card.getColor() == CardColor.BLUE) {
				counts[3]++;
			}
		}

		int bestIndex = 0;
		for (int i = 1; i < counts.length; i++) {
			if (counts[i] > counts[bestIndex]) {
				bestIndex = i;
			}
		}

		if (bestIndex == 0) {
			return CardColor.RED;
		}
		if (bestIndex == 1) {
			return CardColor.YELLOW;
		}
		if (bestIndex == 2) {
			return CardColor.GREEN;
		}
		return CardColor.BLUE;
	}

	private boolean canPlay(Card cardToPlay, Card currentTop) {
		if (cardToPlay == null || currentTop == null) {
			return false;
		}
		if (cardToPlay.isWildcard()) {
			return true;
		}

		if (currentTop.getColor() == CardColor.WILD) {
			return cardToPlay.getColor() == currentTop.getColor();
		}

		return cardToPlay.getColor() == currentTop.getColor()
				|| cardToPlay.getValue() == currentTop.getValue();
	}

	public static void main(String[] args) {
		GameUNO game = new GameUNO();
		game.play();
	}
}
