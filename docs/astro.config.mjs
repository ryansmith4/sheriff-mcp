import { defineConfig } from 'astro/config';
import mermaid from 'astro-mermaid';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
  site: 'https://ryansmith4.github.io',
  base: '/sheriff-mcp',
  integrations: [
    mermaid({ mermaidConfig: { flowchart: { wrappingWidth: 400 } } }),
    starlight({
      title: 'Sheriff',
      description: 'AI-powered static analysis issue fixer - MCP server for SARIF reports',
      head: [
        {
          tag: 'script',
          content: `;(function(){var t=localStorage.getItem('starlight-theme');if(!t){localStorage.setItem('starlight-theme','dark');document.documentElement.dataset.theme='dark';}})();`,
        },
      ],
      components: {
        TableOfContents: './src/components/TableOfContents.astro',
      },
      social: [
        { icon: 'github', label: 'GitHub', href: 'https://github.com/ryansmith4/sheriff-mcp' },
      ],
      editLink: {
        baseUrl: 'https://github.com/ryansmith4/sheriff-mcp/edit/main/docs/',
      },
      customCss: ['./src/styles/custom.css'],
      sidebar: [
        {
          label: 'Getting Started',
          items: [
            { label: 'Overview', slug: 'getting-started' },
            { label: 'Installation', slug: 'getting-started/installation' },
            { label: 'Agent Setup', slug: 'getting-started/agent-setup' },
          ],
        },
        {
          label: 'Tool Reference',
          items: [
            { label: 'Overview', slug: 'tools' },
            { label: 'load', slug: 'tools/load' },
            { label: 'next', slug: 'tools/next' },
            { label: 'done', slug: 'tools/done' },
            { label: 'progress', slug: 'tools/progress' },
            { label: 'summary', slug: 'tools/summary' },
            { label: 'reopen', slug: 'tools/reopen' },
            { label: 'export', slug: 'tools/export' },
          ],
        },
        {
          label: 'Guides',
          autogenerate: { directory: 'guides' },
        },
        {
          label: 'Reference',
          autogenerate: { directory: 'reference' },
        },
      ],
    }),
  ],
});
