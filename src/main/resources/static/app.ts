import { Component, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { HttpModule, Http } from '@angular/http';

@Component({
  selector: 'chess-app',
  template: `
    <h1>Chess Game</h1>
    <div class="game-info">
      <p>{{gameState.whiteTurn ? 'Your turn (White)' : 'Computer thinking...'}}</p>
      <button (click)="newGame()">New Game</button>
    </div>
    <div class="chess-board">
      <div *ngFor="let row of gameState.board; let i = index" class="board-row">
        <div *ngFor="let cell of row; let j = index" 
             class="board-cell {{(i+j)%2 === 0 ? 'light' : 'dark'}} {{selectedSquare?.row === i && selectedSquare?.col === j ? 'selected' : ''}}"
             (click)="onSquareClick(i, j)">
          {{cell}}
        </div>
      </div>
    </div>
  `,
  styles: [`
    .chess-board { display: inline-block; border: 2px solid #333; }
    .board-row { display: flex; }
    .board-cell { width: 60px; height: 60px; display: flex; align-items: center; justify-content: center; font-size: 40px; cursor: pointer; }
    .light { background-color: #f0d9b5; }
    .dark { background-color: #b58863; }
    .selected { background-color: #ffff00 !important; }
    .game-info { margin-bottom: 20px; }
  `]
})
export class ChessComponent {
  gameState: any = { board: [], whiteTurn: true, gameOver: false };
  selectedSquare: any = null;
  
  constructor(private http: Http) {
    this.loadBoard();
  }
  
  loadBoard() {
    this.http.get('/api/board').subscribe(response => {
      this.gameState = response.json();
    });
  }
  
  onSquareClick(row: number, col: number) {
    if (!this.gameState.whiteTurn) return;
    
    if (!this.selectedSquare) {
      if (this.gameState.board[row][col] && this.isPieceWhite(this.gameState.board[row][col])) {
        this.selectedSquare = { row, col };
      }
    } else {
      this.makeMove(this.selectedSquare.row, this.selectedSquare.col, row, col);
      this.selectedSquare = null;
    }
  }
  
  isPieceWhite(piece: string): boolean {
    return '♔♕♖♗♘♙'.includes(piece);
  }
  
  makeMove(fromRow: number, fromCol: number, toRow: number, toCol: number) {
    const move = { fromRow, fromCol, toRow, toCol };
    this.http.post('/api/move', move).subscribe(response => {
      this.gameState = response.json();
    });
  }
  
  newGame() {
    this.http.post('/api/newgame', {}).subscribe(response => {
      this.gameState = response.json();
      this.selectedSquare = null;
    });
  }
}

@NgModule({
  imports: [BrowserModule, HttpModule],
  declarations: [ChessComponent],
  bootstrap: [ChessComponent]
})
export class AppModule { }

platformBrowserDynamic().bootstrapModule(AppModule);