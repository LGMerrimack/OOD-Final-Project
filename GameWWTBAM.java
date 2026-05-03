import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class GameWWTBAM {

	private static final String QUESTIONS_FILE = "wwtbam_questions.txt";
	private static final int[] PRIZE_VALUES = {
			1000,
			5000,
			10000,
			25000,
			50000,
			100000,
			250000,
			500000,
			750000,
			1000000
	};

	private static class Question {
		private final String prompt;
		private final String[] answers;
		private final char correctChoice;

		Question(String prompt, String[] answers, char correctChoice) {
			this.prompt = prompt;
			this.answers = answers;
			this.correctChoice = correctChoice;
		}
	}

	public static void main(String[] args) {
		GameWWTBAM game = new GameWWTBAM();
		game.play();
	}

	public void play() {
		List<Question> questions;
		try {
			questions = loadQuestions(Paths.get(QUESTIONS_FILE));
		} catch (IOException e) {
			System.out.println("Could not read questions file: " + QUESTIONS_FILE);
			System.out.println("Error: " + e.getMessage());
			return;
		} catch (IllegalArgumentException e) {
			System.out.println("Question file format error: " + e.getMessage());
			return;
		}

		if (questions.size() < PRIZE_VALUES.length) {
			System.out.println("Need at least " + PRIZE_VALUES.length + " questions in " + QUESTIONS_FILE + ".");
			return;
		}

		Collections.shuffle(questions);
		List<Question> gameQuestions = questions.subList(0, PRIZE_VALUES.length);

		runGameLoop(gameQuestions);
	}

	private void runGameLoop(List<Question> gameQuestions) {
		Scanner scanner = new Scanner(System.in);
		int winnings = 0;

		System.out.println("Welcome to Who Wants to Be a Millionaire!");
		System.out.println("Answer all 10 questions to win $1,000,000.");
		System.out.println("Type A, B, C, or D. Type QUIT to leave with your current winnings.");

		for (int i = 0; i < PRIZE_VALUES.length; i++) {
			Question q = gameQuestions.get(i);
			int prizeForQuestion = PRIZE_VALUES[i];

			System.out.println("\nQuestion " + (i + 1) + " for $" + formatMoney(prizeForQuestion));
			System.out.println(q.prompt);
			System.out.println("A) " + q.answers[0]);
			System.out.println("B) " + q.answers[1]);
			System.out.println("C) " + q.answers[2]);
			System.out.println("D) " + q.answers[3]);

			char playerChoice = promptForChoice(scanner);
			if (playerChoice == 'Q') {
				System.out.println("You walked away with $" + formatMoney(winnings) + ".");
				return;
			}

			if (playerChoice == q.correctChoice) {
				winnings = prizeForQuestion;
				System.out.println("Correct! You now have $" + formatMoney(winnings) + ".");
			} else {
				System.out.println("Incorrect. The correct answer was " + q.correctChoice + ".");
				System.out.println("Game over. You leave with $" + formatMoney(0) + ".");
				return;
			}
		}

		System.out.println("\nYou answered all 10 questions correctly!");
		System.out.println("You are a millionaire and won $" + formatMoney(1000000) + "!");
	}

	private char promptForChoice(Scanner scanner) {
		while (true) {
			System.out.print("Your answer: ");
			String input = scanner.nextLine().trim().toUpperCase(Locale.ROOT);

			if (input.equals("QUIT")) {
				return 'Q';
			}

			if (input.length() == 1) {
				char choice = input.charAt(0);
				if (choice == 'A' || choice == 'B' || choice == 'C' || choice == 'D') {
					return choice;
				}
			}

			System.out.println("Please enter A, B, C, D, or QUIT.");
		}
	}

	private List<Question> loadQuestions(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path);
		List<Question> questions = new ArrayList<>();
		int index = 0;

		while (index < lines.size()) {
			while (index < lines.size() && lines.get(index).trim().isEmpty()) {
				index++;
			}

			if (index >= lines.size()) {
				break;
			}

			if (index + 5 >= lines.size()) {
				throw new IllegalArgumentException("Incomplete question block at line " + (index + 1));
			}

			String questionLine = lines.get(index++).trim();
			String aLine = lines.get(index++).trim();
			String bLine = lines.get(index++).trim();
			String cLine = lines.get(index++).trim();
			String dLine = lines.get(index++).trim();
			String answerLine = lines.get(index++).trim();

			if (!questionLine.startsWith("Q:")) {
				throw new IllegalArgumentException("Expected Q: at line " + index);
			}
			if (!aLine.startsWith("A:")) {
				throw new IllegalArgumentException("Expected A: at line " + (index - 4));
			}
			if (!bLine.startsWith("B:")) {
				throw new IllegalArgumentException("Expected B: at line " + (index - 3));
			}
			if (!cLine.startsWith("C:")) {
				throw new IllegalArgumentException("Expected C: at line " + (index - 2));
			}
			if (!dLine.startsWith("D:")) {
				throw new IllegalArgumentException("Expected D: at line " + (index - 1));
			}
			if (!answerLine.startsWith("ANSWER:")) {
				throw new IllegalArgumentException("Expected ANSWER: at line " + index);
			}

			String prompt = questionLine.substring(2).trim();
			String[] answers = {
					aLine.substring(2).trim(),
					bLine.substring(2).trim(),
					cLine.substring(2).trim(),
					dLine.substring(2).trim()
			};

			String answerValue = answerLine.substring("ANSWER:".length()).trim().toUpperCase(Locale.ROOT);
			if (answerValue.length() != 1) {
				throw new IllegalArgumentException("ANSWER must be one letter at line " + index);
			}

			char correctChoice = answerValue.charAt(0);
			if (correctChoice != 'A' && correctChoice != 'B' && correctChoice != 'C' && correctChoice != 'D') {
				throw new IllegalArgumentException("ANSWER must be A, B, C, or D at line " + index);
			}

			questions.add(new Question(prompt, answers, correctChoice));
		}

		return questions;
	}

	private String formatMoney(int value) {
		return String.format(Locale.US, "%,d", value);
	}
}
