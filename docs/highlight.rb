require 'rouge'

source = File.read(ARGV[0])
formatter = Rouge::Formatters::HTML.new
lexer = Rouge::Lexers::GraphQL.new
puts formatter.format(lexer.lex(source))
