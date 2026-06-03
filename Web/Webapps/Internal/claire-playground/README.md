# Claire Playground

A React-based playground for testing and interacting with Claire, the AI chatbot from Clear30. This app allows you to chat with Claire using your own OpenAI API key without connecting to any backend services.

## Features

- **API Key Input**: Securely enter your OpenAI API key (stored only in memory)
- **Model Selection**: Choose from different OpenAI models (GPT-4, GPT-4-turbo, GPT-4o, GPT-4o-mini, GPT-3.5-turbo)
- **Realistic Context**: Pre-loaded with dummy user data that simulates a real Clear30 user
- **Chat Interface**: Clean, modern chat interface with message history
- **No Backend Required**: Direct integration with OpenAI's completion API
- **Session Reset**: Easy reset functionality to start fresh

## Getting Started

### Prerequisites

- Node.js (v14 or higher)
- npm or yarn
- OpenAI API key

### Installation

1. Navigate to the project directory:
```bash
cd claire-playground
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm start
```

4. Open [http://localhost:3000](http://localhost:3000) in your browser

### Usage

1. **Enter API Key**: On first load, enter your OpenAI API key and select your preferred model
2. **Start Chatting**: Begin chatting with Claire using the dummy user context
3. **View Context**: Expand the "Current User Context" section to see the dummy data being used
4. **Reset Session**: Click "Reset Session" to clear the chat and start over with a new API key

## Dummy User Context

The playground uses realistic dummy data for testing:

- **Name**: Alex Johnson
- **Program**: Clear30 (30-day cannabis break)
- **Current Day**: 12
- **Usage History**: Daily use for 3+ years, primarily evenings and weekends
- **Goals**: Reset tolerance and improve mental clarity
- **Recent Progress**: 12 days cannabis-free with improving sleep and focus

## Technical Details

- **Frontend**: React with TypeScript
- **Styling**: Custom CSS with modern design
- **API Integration**: OpenAI JavaScript SDK
- **No Data Persistence**: All data is stored in memory only
- **No Backend**: Direct browser-to-OpenAI communication

## Security Notes

- API keys are only stored in browser memory
- No data is sent to any external servers except OpenAI
- Page refresh clears all data including API keys
- No conversation history is persisted

## Available Scripts

- `npm start` - Start development server
- `npm run build` - Build for production
- `npm test` - Run tests
- `npm run eject` - Eject from Create React App

## API Models Supported

- GPT-4o Mini (Recommended for cost-effectiveness)
- GPT-4o
- GPT-4 Turbo
- GPT-4
- GPT-3.5 Turbo

## Troubleshooting

### Common Issues

1. **"Invalid API key"**: Ensure your OpenAI API key starts with "sk-" and is valid
2. **"Rate limit exceeded"**: Wait a moment before trying again, or check your OpenAI usage limits
3. **"Insufficient quota"**: Check your OpenAI account balance and usage

### Browser Compatibility

This app works best in modern browsers that support:
- ES6+ JavaScript features
- CSS Grid and Flexbox
- Modern browser APIs

## Contributing

This is a development tool for testing Claire's responses. Modifications should focus on:
- UI/UX improvements
- Additional model support
- Better error handling
- Enhanced debugging features

## License

This project is part of the Clear30 ecosystem and follows the same licensing terms.
