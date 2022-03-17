package com.example.matchgamesample.game;

import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.matchgamesample.MainActivity;
import com.example.matchgamesample.R;
import com.example.matchgamesample.engine.GameEngine;
import com.example.matchgamesample.engine.GameEvent;
import com.example.matchgamesample.engine.GameObject;
import com.example.matchgamesample.engine.InputController;
import com.example.matchgamesample.fragment.MapFragment;
import com.example.matchgamesample.fragment.WinDialogFragment;
import com.example.matchgamesample.game.algorithm.BonusTimeAlgorithm;
import com.example.matchgamesample.game.algorithm.GameAlgorithm;
import com.example.matchgamesample.game.state.GameStateAnim;

public class GameController extends GameObject {
    private final GameEngine mGameEngine;
    private final Tile[][] mTileArray;
    private final int mRow, mColumn;
    private final int mTileSize;
    private final InputController mInputController;
    private final GameStateAnim mGameStateAnim;
    private GameAlgorithm mAlgorithm;
    private final BonusTimeAlgorithm mBonusTimeAlgorithm;

    private GameControllerState mState;
    private final Button mSkipButton;
    private int mWaitingTime;
    private static final int WAITING_TIME = 1500;
    private static final int SWAP_THRESHOLD = 50;

    public GameController(GameEngine gameEngine, Tile[][] TileArray) {
        mGameEngine = gameEngine;
        mTileArray = TileArray;
        mRow = gameEngine.mLevel.mRow;
        mColumn = gameEngine.mLevel.mColumn;
        mTileSize = gameEngine.mImageSize;
        mInputController = gameEngine.mInputController;
        mGameStateAnim = new GameStateAnim(gameEngine);
        mBonusTimeAlgorithm = new BonusTimeAlgorithm(gameEngine);
        mSkipButton = (Button) mGameEngine.mActivity.findViewById(R.id.btn_skip);
    }

    public void setMyAlgorithm(GameAlgorithm gameAlgorithm) {
        mAlgorithm = gameAlgorithm;
    }

    @Override
    public void startGame() {
        // Start the tile
        for (int i = 0; i < mRow; i++) {
            for (int j = 0; j < mColumn; j++) {
                mTileArray[i][j].startGame();
            }
        }

        mWaitingTime = 0;
        mState = GameControllerState.START_GAME;
        mGameStateAnim.startGameBoard();
    }

    @Override
    public void onUpdate(long elapsedMillis) {
        switch (mState) {
            case START_GAME:
                mWaitingTime += elapsedMillis;
                if (mWaitingTime > WAITING_TIME) {
                    // Start animation
                    mGameStateAnim.startGame(mGameEngine.mLevel.mLevelType);

                    // We disable player move when waiting
                    mState = GameControllerState.WAITING;
                    mWaitingTime = 0;

                    // Start hint
                    mGameEngine.onGameEvent(GameEvent.START_HINT);
                }
                break;
            case PLAY_GAME:
                mAlgorithm.update(mTileArray, elapsedMillis);
                break;
            case WAITING:
                mWaitingTime += elapsedMillis;
                if (mWaitingTime > WAITING_TIME) {
                    mState = GameControllerState.PLAY_GAME;
                    mWaitingTime = 0;
                }
                break;
            case BONUS_TIME:
                mBonusTimeAlgorithm.update(mTileArray, elapsedMillis);
                break;
            case BONUS_TIME_WAITING:
                mWaitingTime += elapsedMillis;
                if (mWaitingTime > 1800) {
                    mState = GameControllerState.BONUS_TIME;
                    mWaitingTime = 0;
                }
                break;
            case GAME_OVER:
                mWaitingTime += elapsedMillis;
                if (mWaitingTime > 2300) {
                    showGameOverDialog();
                    mWaitingTime = 0;
                }
                break;
            case GAME_COMPLETE:
                mWaitingTime += elapsedMillis;
                if (mWaitingTime > 700) {
                    showGameCompleteDialog();
                    mWaitingTime = 0;
                }
                break;
        }
    }

    @Override
    public void onDraw() {
        for (int i = 0; i < mRow; i++) {
            for (int j = 0; j < mColumn; j++) {
                mTileArray[i][j].onDraw();
            }
        }
    }

    @Override
    public void onGameEvent(GameEvent gameEvents) {
        switch (gameEvents) {
            case PLAYER_TOUCH:
                if (mState != GameControllerState.PLAY_GAME || !mAlgorithm.canPlayerSwap()) {
                    return;
                }
                int touchCol = mInputController.mX_Down / mTileSize;
                int touchRow = mInputController.mY_Down / mTileSize;
                mTileArray[touchRow][touchCol].isChosen = true;
                break;
            case PLAYER_RELEASE:
                if (mState != GameControllerState.PLAY_GAME || !mAlgorithm.canPlayerSwap()) {
                    return;
                }
                int releaseCol = mInputController.mX_Down / mTileSize;
                int releaseRow = mInputController.mY_Down / mTileSize;
                mTileArray[releaseRow][releaseCol].isChosen = false;
                break;
            case PLAYER_MOVE:
                if (mState != GameControllerState.PLAY_GAME || !mAlgorithm.canPlayerSwap()) {
                    return;
                }
                swapTile();
                break;
            case PLAYER_REACH_TARGET:
                mGameStateAnim.gameOver(GameEvent.PLAYER_REACH_TARGET);
                mState = GameControllerState.BONUS_TIME_WAITING;
                break;
            case PLAYER_OUT_OF_MOVE:
                mGameStateAnim.gameOver(GameEvent.PLAYER_OUT_OF_MOVE);
                clearView(1600);
                mState = GameControllerState.GAME_OVER;
                break;
            case BONUS_TIME:
                mGameStateAnim.startBonusTime();
                addSkipButton();
                mState = GameControllerState.BONUS_TIME_WAITING;
                break;
            case BONUS_TIME_COMPLETE:
                clearView(0);
                mState = GameControllerState.GAME_COMPLETE;
                break;
            case REFRESH:
                mGameStateAnim.refreshGame();
                refreshTile();
                mState = GameControllerState.WAITING;
                break;
            case COMBO_4:
                mGameStateAnim.startCombo(GameEvent.COMBO_4);
                mState = GameControllerState.WAITING;
                break;
            case COMBO_5:
                mGameStateAnim.startCombo(GameEvent.COMBO_5);
                mState = GameControllerState.WAITING;
                break;
            case COMBO_6:
                mGameStateAnim.startCombo(GameEvent.COMBO_6);
                mState = GameControllerState.WAITING;
                break;
        }
    }

    private void swapTile() {
        // Get the tile player press from inputController
        int swapCol = mInputController.mX_Down / mTileSize;
        int swapRow = mInputController.mY_Down / mTileSize;
        int swapCol2 = 0;
        int swapRow2 = 0;

        if (mInputController.mX_Down - mInputController.mX_Up < -SWAP_THRESHOLD) {
            // Swap right
            if (swapCol >= mColumn - 1)
                return;
            swapCol2 = swapCol + 1;
            swapRow2 = swapRow;
        } else if (mInputController.mX_Down - mInputController.mX_Up > SWAP_THRESHOLD) {
            // Swap left
            if (swapCol <= 0)
                return;
            swapCol2 = swapCol - 1;
            swapRow2 = swapRow;
        } else if (mInputController.mY_Down - mInputController.mY_Up > SWAP_THRESHOLD) {
            // Swap up
            if (swapRow <= 0)
                return;
            swapCol2 = swapCol;
            swapRow2 = swapRow - 1;
        } else if (mInputController.mY_Down - mInputController.mY_Up < -SWAP_THRESHOLD) {
            // Swap down
            if (swapRow >= mRow - 1)
                return;
            swapCol2 = swapCol;
            swapRow2 = swapRow + 1;
        } else {
            return;
        }

        if (!mTileArray[swapRow][swapCol].isMovable()
                || !mTileArray[swapRow2][swapCol2].isMovable()) {
            return;
        }

        // Update Algorithm state
        mAlgorithm.checkSpecialCombine(mTileArray[swapRow][swapCol], mTileArray[swapRow2][swapCol2]);
        mAlgorithm.swap(mTileArray, mTileArray[swapRow][swapCol], mTileArray[swapRow2][swapCol2]);
        mAlgorithm.swapCol = swapCol;
        mAlgorithm.swapRow = swapRow;
        mAlgorithm.swapCol2 = swapCol2;
        mAlgorithm.swapRow2 = swapRow2;
        mAlgorithm.isSwap = true;
        mAlgorithm.mMoveTile = true;
        mAlgorithm.mShowHint = true;

    }

    private void refreshTile() {
        mAlgorithm.refresh(mTileArray);
    }

    private void addSkipButton() {
        mSkipButton.animate().setStartDelay(300).setDuration(400)
                .scaleX(2).scaleY(2).alpha(1).setInterpolator(new OvershootInterpolator());
        mSkipButton.setVisibility(View.VISIBLE);
        mSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Tile.mSpeed = 100;
                mBonusTimeAlgorithm.mCurrentBonusTimeInterval = 50;
                mBonusTimeAlgorithm.mCurrentWaitingTime = 0;
                mSkipButton.setVisibility(View.INVISIBLE);
            }
        });

        ConstraintLayout board_button = (ConstraintLayout) mGameEngine.mActivity.findViewById(R.id.board_button);
        board_button.setVisibility(View.INVISIBLE);
    }

    private void clearView(int delay) {
        // Broad disappear
        mGameStateAnim.clearGameBoard(delay);
        mSkipButton.setVisibility(View.GONE);
    }

    private void showGameOverDialog() {
        MainActivity mainActivity = (MainActivity) mGameEngine.mActivity;
        mainActivity.navigateToFragment(new MapFragment());
    }

    private void showGameCompleteDialog() {
        MainActivity mainActivity = (MainActivity) mGameEngine.mActivity;
        mainActivity.navigateToFragment(WinDialogFragment
                .newInstance(mGameEngine.mLevel.mLevel, mGameEngine.mLevel.mScore));
    }

}
