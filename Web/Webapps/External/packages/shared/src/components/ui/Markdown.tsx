import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { TextSizes } from './TextSizes';

export interface MarkdownProps {
  children: string;
  className?: string;
  style?: React.CSSProperties;
}

export const Markdown: React.FC<MarkdownProps> = ({
  children,
  className = '',
  style
}) => {
  return (
    <div className={className} style={style}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // Override heading components to use smaller TextSizes
          h1: ({ children }) => <TextSizes.Heading3 style={{ fontSize: '20px' }}>{children}</TextSizes.Heading3>,
          h2: ({ children }) => <TextSizes.Heading3 style={{ fontSize: '18px' }}>{children}</TextSizes.Heading3>,
          h3: ({ children }) => <TextSizes.Small style={{ fontSize: '16px' }}>{children}</TextSizes.Small>,
          h4: ({ children }) => <TextSizes.Small style={{ fontSize: '15px' }}>{children}</TextSizes.Small>,
          h5: ({ children }) => <TextSizes.Tiny style={{ fontSize: '14px' }}>{children}</TextSizes.Tiny>,
          h6: ({ children }) => <TextSizes.Tiny style={{ fontSize: '13px' }}>{children}</TextSizes.Tiny>,
          
          // Override paragraph to use smaller TextSizes
          p: ({ children }) => <TextSizes.Tiny style={{ marginBottom: '14px', fontSize: '14px' }}>{children}</TextSizes.Tiny>,
          
          // Override list items to use smaller TextSizes
          li: ({ children }) => <TextSizes.Tiny style={{ fontSize: '14px' }}>{children}</TextSizes.Tiny>,
          
          // Override table components for better styling with smaller text
          table: ({ children }) => (
            <div className="overflow-x-auto my-4">
              <table className="w-full border-collapse border border-gray-300">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => <thead className="bg-gray-100">{children}</thead>,
          tbody: ({ children }) => <tbody>{children}</tbody>,
          tr: ({ children }) => <tr className="border-b border-gray-300">{children}</tr>,
          th: ({ children }) => (
            <th className="border border-gray-300 px-3 py-2 text-left font-semibold">
              <TextSizes.Tiny style={{ fontSize: '13px' }}>{children}</TextSizes.Tiny>
            </th>
          ),
          td: ({ children }) => (
            <td className="border border-gray-300 px-3 py-2">
              <TextSizes.Tiny style={{ fontSize: '13px' }}>{children}</TextSizes.Tiny>
            </td>
          ),
          
          // Override code blocks with smaller text
          code: ({ children, className }) => {
            const isInline = !className;
            if (isInline) {
              return <code className="bg-gray-100 px-1 py-0.5 rounded text-xs font-mono">{children}</code>;
            }
            return (
              <pre className="bg-gray-100 p-3 rounded overflow-x-auto my-4">
                <code className="text-xs font-mono">{children}</code>
              </pre>
            );
          },
          
          // Override blockquotes with smaller text
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-gray-300 pl-4 my-4 italic text-gray-600 text-sm">
              {children}
            </blockquote>
          ),
          
          // Override links with smaller text
          a: ({ children, href }) => (
            <a href={href} className="text-blue-600 hover:text-blue-800 underline text-sm">
              {children}
            </a>
          ),
          
          // Override strong and emphasis with smaller text
          strong: ({ children }) => <strong className="font-semibold text-sm">{children}</strong>,
          em: ({ children }) => <em className="italic text-sm">{children}</em>,
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
};
